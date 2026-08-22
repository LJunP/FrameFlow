"""OpenAI 兼容适配器：任何 chat/completions 形态的服务
（OpenAI / GLM / DeepSeek / 本地 vLLM…）都走这一个适配器。

无 Key 自动禁用（ProviderDisabled）→ 编排层转为语义 ERROR 进人工复核。
预算与超时：单次请求超时 + 单 run 最大 token 估算，超限抛 ProviderError。
"""

from __future__ import annotations

import json
import os

import requests

from .base import (ProviderDisabled, ProviderError, SemanticRequest,
                   SemanticResult, SemanticVerdict)

VALID_VERDICTS = {"PASS", "VIOLATE", "UNKNOWN"}


class OpenAICompatProvider:
    name = "semantic-openai-compat"
    version = "1"

    def __init__(self, base_url: str | None = None, api_key: str | None = None,
                 model: str | None = None, timeout_s: float = 30.0,
                 max_prompt_chars: int = 12000):
        self.base_url = (base_url or os.environ.get("FRAMEFLOW_SEMANTIC_BASE_URL", "")).rstrip("/")
        self.api_key = api_key or os.environ.get("FRAMEFLOW_SEMANTIC_API_KEY", "")
        self.model = model or os.environ.get("FRAMEFLOW_SEMANTIC_MODEL", "gpt-4o-mini")
        self.timeout_s = timeout_s
        self.max_prompt_chars = max_prompt_chars

    @property
    def available(self) -> bool:
        return bool(self.base_url and self.api_key)

    def analyze(self, request: SemanticRequest) -> SemanticResult:
        if not self.available:
            raise ProviderDisabled(
                "未配置 FRAMEFLOW_SEMANTIC_BASE_URL/API_KEY，语义 Provider 禁用")

        prompt = build_prompt(request)
        if len(prompt) > self.max_prompt_chars:
            # 预算边界：过长的 brief/维度清单截断，而不是把钱包交给模型
            prompt = prompt[: self.max_prompt_chars] + "\n...(truncated)"

        payload = {
            "model": self.model,
            "messages": [{"role": "user", "content": prompt}],
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
            raise ProviderError(f"语义模型调用失败: {e}") from e

        verdicts = parse_verdicts(content, request)
        return SemanticResult(verdicts=verdicts, prompt_snapshot=prompt,
                              raw_output=content, provider=self.name,
                              provider_version=self.version)


def build_prompt(request: SemanticRequest) -> str:
    dims = ", ".join(request.dimensions)
    return (
        "你是视频质检助手。根据关键帧与创作要求判断视频的语义质量。\n"
        f"创作要求(Brief): {request.brief_content}\n"
        f"需要检查的维度: {dims}\n"
        f"关键帧数量: {len(request.frames_jpeg)}\n"
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
