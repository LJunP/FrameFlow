"""语义 Provider SPI——业务只依赖这里的抽象，不依赖任何具体模型厂商。

设计原则（docs/02 §4.6）：
- 可替换：换模型 = 换适配器，编排代码零改动；
- 可 Mock：FakeProvider 确定性输出，让全链路可测；
- 无 Key 自动禁用：没有凭据时抛 ProviderDisabled，编排层把它变成
  语义 ERROR Finding（进人工复核），而不是让任务失败。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Protocol


class ProviderError(Exception):
    """调用失败（网络/超时/限流）→ 语义 ERROR，人工复核。"""


class ProviderDisabled(Exception):
    """未配置凭据/开关 → 语义 ERROR（附原因），绝不静默跳过。"""


@dataclass(frozen=True)
class SemanticRequest:
    brief_content: str
    frames_jpeg: list[bytes]           # 关键帧（JPEG 字节）
    frame_timecodes_ms: list[int]
    dimensions: list[str]              # 要检查的语义维度
    candidate_hint: str = ""           # 元信息（文件名等），仅用于日志


@dataclass(frozen=True)
class SemanticVerdict:
    dimension: str
    verdict: str                       # PASS / VIOLATE / UNKNOWN
    reason: str
    timecode_ms: int | None = None


@dataclass(frozen=True)
class SemanticResult:
    verdicts: list[SemanticVerdict]
    prompt_snapshot: str               # 发给模型的完整 prompt（证据的一部分）
    raw_output: str                    # 模型原始回复
    provider: str
    provider_version: str
    degraded: bool = False             # True=降级产物（禁用/失败）


class SemanticProvider(Protocol):
    name: str
    version: str

    def analyze(self, request: SemanticRequest) -> SemanticResult:
        ...
