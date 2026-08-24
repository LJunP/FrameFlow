"""OpenAI Responses API 兼容的多模态适配器。

该适配器与 ``openai-compat`` 的 Chat Completions 路由严格分离：目录中只有
显式声明 ``openai-responses`` 的模型才会走 ``POST /responses``。它复用相同
的提示词、关键帧预算和 JSONL 判定解析，避免同一质检任务因协议不同产生
不同的 fail-closed 语义。
"""

from __future__ import annotations

import base64
import os

import requests

from .base import (ProviderDisabled, ProviderError, SemanticRequest,
                   SemanticResult)
from .openai_compat import MAX_KEYFRAMES, build_prompt, parse_verdicts


class OpenAIResponsesProvider:
    """通过非流式 Responses API 调用支持视觉输入的模型。"""

    name = "semantic-openai-responses"
    version = "1"

    def __init__(self, base_url: str | None = None,
                 api_key: str | None = None, model: str | None = None,
                 timeout_s: float = 30.0, max_prompt_chars: int = 12000,
                 model_id: str = "platform-default"):
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
        try:
            response = requests.post(
                f"{self.base_url}/responses",
                headers={"Authorization": f"Bearer {self.api_key}"},
                json=payload, timeout=self.timeout_s)
            response.raise_for_status()
            output_text = _extract_output_text(response.json())
        except requests.Timeout:
            # 不传播 requests 原始异常；其中可能包含完整 baseUrl。
            raise ProviderError(f"语义模型超时({self.timeout_s}s)") from None
        except requests.RequestException:
            # Key 只进入请求头；产品异常与证据只能看到固定的脱敏文案。
            raise ProviderError("语义模型调用失败") from None
        except (KeyError, IndexError, TypeError, ValueError):
            raise ProviderError("语义模型响应格式无效") from None

        verdicts = parse_verdicts(output_text, request)
        return SemanticResult(
            verdicts=verdicts,
            prompt_snapshot=prompt,
            raw_output=output_text,
            provider=self.name,
            provider_version=self.version,
            model_id=self.model_id,
            model=self.model,
        )


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
