"""OpenAI Chat Completions 兼容的多模态适配器。

只有同时兼容文字 + ``image_url`` 内容块的视觉模型端点才能使用；仅兼容
文本 Chat Completions 的模型不能因为 URL 形状相同就视为可用。

无 Key 自动禁用（ProviderDisabled）→ 编排层转为语义 ERROR 进人工复核。
预算与超时：单次请求超时 + 单 run 最大 token 估算，超限抛 ProviderError。
"""

from __future__ import annotations

import base64
import json
import os

import requests

from .base import (ProviderDisabled, ProviderError, SemanticRequest,
                   SemanticResult, SemanticVerdict)

VALID_VERDICTS = {"PASS", "VIOLATE", "UNKNOWN"}
MAX_KEYFRAMES = 3


class OpenAICompatProvider:
    name = "semantic-openai-compat"
    version = "1"

    def __init__(self, base_url: str | None = None, api_key: str | None = None,
                 model: str | None = None, timeout_s: float = 30.0,
                 max_prompt_chars: int = 12000,
                 model_id: str = "platform-default"):
        # ``None`` 才表示读取 legacy 环境；目录路由显式传入空 Key 时不能
        # 又回退到另一套全局 Key，否则租户选择会跨模型串凭据。
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
            # 预算边界：过长的 brief/维度清单截断，而不是把钱包交给模型
            prompt = prompt[: self.max_prompt_chars] + "\n...(truncated)"

        # ★ 核心：Chat Completions 多模态输入必须把 JPEG 真正放进 content；
        # 只在文字里写“关键帧数量”时，模型其实完全看不到视频画面。
        message_content: list[dict] = [{"type": "text", "text": prompt}]
        for jpeg in request.frames_jpeg[:MAX_KEYFRAMES]:
            encoded = base64.b64encode(jpeg).decode("ascii")
            message_content.append({
                "type": "image_url",
                "image_url": {"url": f"data:image/jpeg;base64,{encoded}"},
            })

        payload = {
            "model": self.model,
            "messages": [{"role": "user", "content": message_content}],
            "temperature": 0,
        }
        try:
            resp = requests.post(
                f"{self.base_url}/chat/completions",
                headers={"Authorization": f"Bearer {self.api_key}"},
                json=payload, timeout=self.timeout_s)
            resp.raise_for_status()
            content = resp.json()["choices"][0]["message"]["content"]
        except requests.Timeout as e:
            raise ProviderError(f"语义模型超时({self.timeout_s}s)") from e
        except requests.RequestException as e:
            # requests 的异常文本通常包含完整 URL；证据只记录逻辑 ID 与
            # 实际模型名，不应把平台端点（更不能把 Key）暴露给产品用户。
            raise ProviderError("语义模型调用失败") from e
        except (KeyError, IndexError, TypeError, ValueError) as e:
            # ★ 核心：兼容服务返回 200 不代表响应契约有效；格式损坏仍属于
            # Provider 故障，应降级为语义 ERROR，而不是拖垮整个视频分析 run。
            raise ProviderError("语义模型响应格式无效") from e

        verdicts = parse_verdicts(content, request)
        return SemanticResult(verdicts=verdicts, prompt_snapshot=prompt,
                              raw_output=content, provider=self.name,
                              provider_version=self.version,
                              model_id=self.model_id, model=self.model)


def build_prompt(request: SemanticRequest) -> str:
    dims = ", ".join(request.dimensions)
    return (
        "你是视频质检助手。根据关键帧与创作要求判断视频的语义质量。\n"
        f"创作要求(Brief): {request.brief_content}\n"
        f"需要检查的维度: {dims}\n"
        f"关键帧数量: {min(len(request.frames_jpeg), MAX_KEYFRAMES)}\n"
        "对每个维度输出一行 JSON：{\"dimension\":..., \"verdict\":\"PASS|VIOLATE|UNKNOWN\","
        " \"reason\":...}\n"
        "只输出 JSON 行，不要其他内容。不确定时必须回答 UNKNOWN，不要猜。"
    )


def parse_verdicts(content: str, request: SemanticRequest) -> list[SemanticVerdict]:
    """宽容解析：逐行找 JSON 对象；解析失败/缺失的维度补 UNKNOWN。"""
    parsed: dict[str, SemanticVerdict] = {}
    for line in content.splitlines():
        line = line.strip().strip("`")
        if not line.startswith("{"):
            continue
        try:
            obj = json.loads(line)
            dim, verdict = obj.get("dimension"), obj.get("verdict", "").upper()
            if dim and verdict in VALID_VERDICTS:
                parsed[dim] = SemanticVerdict(
                    dimension=dim, verdict=verdict,
                    reason=str(obj.get("reason", ""))[:500],
                    timecode_ms=request.frame_timecodes_ms[0]
                    if request.frame_timecodes_ms else None)
        except json.JSONDecodeError:
            continue
    # 模型漏答的维度按 UNKNOWN 处理（宁可人工复核，不可默认合格）
    return [parsed.get(dim, SemanticVerdict(dim, "UNKNOWN", "模型未作答"))
            for dim in request.dimensions]
