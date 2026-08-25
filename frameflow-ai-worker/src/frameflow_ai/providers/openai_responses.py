"""OpenAI Responses API 兼容的多模态适配器。

该适配器与 ``openai-compat`` 的 Chat Completions 路由严格分离：目录中只有
显式声明 ``openai-responses`` 的模型才会走 ``POST /responses``。它复用相同
的提示词、关键帧预算和 JSONL 判定解析，避免同一质检任务因协议不同产生
不同的 fail-closed 语义。
"""

from __future__ import annotations

import base64
import os
import re
import threading
from collections.abc import Callable

import requests

from .base import (ProviderDisabled, ProviderError, SemanticRequest,
                   SemanticResult)
from .openai_compat import MAX_KEYFRAMES, build_prompt, parse_verdicts


_REQUEST_BUDGET_LOCK = threading.Lock()
_REQUEST_COUNTS: dict[str, int] = {}
_BUDGET_ID_PATTERN = re.compile(r"[A-Za-z0-9._-]{1,96}")


class OpenAIResponsesProvider:
    """通过非流式 Responses API 调用支持视觉输入的模型。"""

    name = "semantic-openai-responses"
    version = "1"

    def __init__(self, base_url: str | None = None,
                 api_key: str | None = None, model: str | None = None,
                 timeout_s: float = 30.0, max_prompt_chars: int = 12000,
                 model_id: str = "platform-default",
                 post: Callable[..., requests.Response] | None = None):
        # 与 Chat Completions 适配器保持同一 legacy 配置语义；目录路由会
        # 显式传入值，因此空 Key 不会回退到另一模型的全局凭据。
        configured_base_url = (os.environ.get("FRAMEFLOW_SEMANTIC_BASE_URL", "")
                               if base_url is None else base_url)
        self.base_url = configured_base_url.rstrip("/")
        self.api_key = (os.environ.get("FRAMEFLOW_SEMANTIC_API_KEY", "")
                        if api_key is None else api_key)
        self.model = (os.environ.get("FRAMEFLOW_SEMANTIC_MODEL", "gpt-4o-mini")
                      if model is None else model)
        self.model_id = model_id
        self.timeout_s = timeout_s
        self.max_prompt_chars = max_prompt_chars
        # 真实门禁通过单请求 transport 包装器注入这里，既复用生产适配器，
        # 又能从结构上限制“最多一次、禁止自动重试”。常规 Worker 仍使用 requests。
        self._post = requests.post if post is None else post

    @property
    def available(self) -> bool:
        return bool(self.base_url and self.api_key)

    def analyze(self, request: SemanticRequest) -> SemanticResult:
        if not self.available:
            raise ProviderDisabled(
                f"模型 {self.model_id} 的端点或凭据未配置，语义 Provider 禁用")

        prompt = build_prompt(request)
        if len(prompt) > self.max_prompt_chars:
            prompt = prompt[: self.max_prompt_chars] + "\n...(truncated)"

        # ★ 核心：Responses API 的图片块是 input_image，而不是 Chat
        # Completions 的 image_url；协议混用会让兼容端点返回 4xx，或更危险
        # 地只处理文字而忽略画面。
        content: list[dict] = [{"type": "input_text", "text": prompt}]
        for jpeg in request.frames_jpeg[:MAX_KEYFRAMES]:
            encoded = base64.b64encode(jpeg).decode("ascii")
            content.append({
                "type": "input_image",
                "image_url": f"data:image/jpeg;base64,{encoded}",
            })

        # Responses 的 temperature 是可选字段；部分推理模型或兼容网关会
        # 拒绝显式采样参数，因此这里采用模型默认值来扩大协议兼容面。
        payload = {
            "model": self.model,
            "input": [{"role": "user", "content": content}],
            "stream": False,
        }
        # ★ 核心：真实门禁一旦领取预算，即使请求超时或解析失败也算已消耗；
        # 后续消息重投会在联网前被拒绝。若把计数放在 HTTP 成功之后，失败路径
        # 就可能违反所有者批准的“最多一次、失败不重试”。
        request_budget, request_budget_id = _request_budget_from_env()
        request_ordinal = _claim_request_budget(request_budget_id, request_budget)
        try:
            response = self._post(
                f"{self.base_url}/responses",
                headers={"Authorization": f"Bearer {self.api_key}"},
                json=payload, timeout=self.timeout_s)
            response.raise_for_status()
            output_text = _extract_output_text(response.json())
        except requests.Timeout:
            # 不传播 requests 原始异常；其中可能包含完整 baseUrl。
            raise ProviderError(
                f"语义模型超时({self.timeout_s}s)",
                request_ordinal=request_ordinal,
                request_budget=request_budget) from None
        except requests.RequestException:
            # Key 只进入请求头；产品异常与证据只能看到固定的脱敏文案。
            raise ProviderError(
                "语义模型调用失败",
                request_ordinal=request_ordinal,
                request_budget=request_budget) from None
        except (KeyError, IndexError, TypeError, ValueError):
            raise ProviderError(
                "语义模型响应格式无效",
                request_ordinal=request_ordinal,
                request_budget=request_budget) from None

        verdicts = parse_verdicts(output_text, request)
        return SemanticResult(
            verdicts=verdicts,
            prompt_snapshot=prompt,
            raw_output=output_text,
            provider=self.name,
            provider_version=self.version,
            model_id=self.model_id,
            model=self.model,
            request_ordinal=request_ordinal,
            request_budget=request_budget,
        )


def _request_budget_from_env() -> tuple[int | None, str | None]:
    """读取可选的进程级真实门禁预算；未设置时完全关闭。"""
    raw_budget = os.environ.get("FRAMEFLOW_PROVIDER_REQUEST_BUDGET", "").strip()
    raw_id = os.environ.get("FRAMEFLOW_PROVIDER_REQUEST_BUDGET_ID", "").strip()
    if not raw_budget and not raw_id:
        return None, None
    if not raw_budget or not raw_id:
        raise ProviderError("真实门禁请求预算配置不完整")
    try:
        budget = int(raw_budget)
    except ValueError:
        raise ProviderError("真实门禁请求预算必须是正整数") from None
    if budget < 1 or budget > 10:
        raise ProviderError("真实门禁请求预算必须在 1..10")
    if _BUDGET_ID_PATTERN.fullmatch(raw_id) is None:
        raise ProviderError("真实门禁请求预算 ID 非法")
    return budget, raw_id


def _claim_request_budget(budget_id: str | None, budget: int | None) -> int | None:
    """原子领取一次联网资格；超过预算时绝不调用 transport。"""
    if budget is None or budget_id is None:
        return None
    with _REQUEST_BUDGET_LOCK:
        consumed = _REQUEST_COUNTS.get(budget_id, 0)
        if consumed >= budget:
            raise ProviderError("真实门禁请求预算已耗尽；未发出额外网络请求")
        ordinal = consumed + 1
        _REQUEST_COUNTS[budget_id] = ordinal
        return ordinal


def _extract_output_text(payload: object) -> str:
    """按 Responses 非流式契约收集所有 ``output_text`` 内容块。"""
    if not isinstance(payload, dict):
        raise ValueError("response must be an object")
    output = payload.get("output")
    if not isinstance(output, list):
        raise ValueError("response.output must be an array")

    parts: list[str] = []
    for item in output:
        # Responses 的 output 可能同时含 reasoning/tool 等非消息项；这些项
        # 没有 content，属于合法但与本适配器无关的输出。
        if not isinstance(item, dict):
            continue
        item_content = item.get("content")
        if not isinstance(item_content, list):
            continue
        for block in item_content:
            if (isinstance(block, dict)
                    and block.get("type") == "output_text"
                    and isinstance(block.get("text"), str)):
                parts.append(block["text"])

    if not parts:
        raise ValueError("response contains no output_text")
    return "\n".join(parts)
