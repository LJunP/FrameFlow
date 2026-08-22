"""检测器包：确定性维度全集（docs/01 §8.1）。"""

from .probe import DetectorError, ProbeResult, probe
from . import rules, frames  # noqa: F401  （pipeline 按需引用）

__all__ = ["DetectorError", "ProbeResult", "probe", "rules", "frames"]
