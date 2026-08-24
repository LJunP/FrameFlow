"""ffprobe 检测器：从视频文件提取容器/流元数据。

无 ffprobe 二进制时抛 DetectorError——由 pipeline 转成 ok=False 的
ANALYSIS_ERROR 结果（系统问题），绝不伪装成视频不合格。
"""

from __future__ import annotations

import json
import shutil
import subprocess
from dataclasses import dataclass
from typing import Optional

DETECTOR_ID = "ffprobe-meta"
DETECTOR_VERSION = "1"


class DetectorError(Exception):
    """检测器自身故障（缺二进制/进程失败/不可解析）→ ANALYSIS_ERROR。"""


@dataclass(frozen=True)
class ProbeResult:
    duration_ms: Optional[int]
    width: Optional[int]
    height: Optional[int]
    fps: Optional[float]
    has_audio: bool

    def to_dict(self) -> dict:
        return {
            "durationMs": self.duration_ms,
            "width": self.width,
            "height": self.height,
            "fps": self.fps,
            "hasAudio": self.has_audio,
        }


def probe(path: str) -> ProbeResult:
    """运行 ffprobe 并解析。任何一步失败都视为检测器故障。"""
    binary = shutil.which("ffprobe")
    if binary is None:
        raise DetectorError("ffprobe 不在 PATH 中（worker 环境不完整）")

    cmd = [
        binary, "-v", "quiet",
        "-print_format", "json",
        "-show_format", "-show_streams",
        path,
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
    if proc.returncode != 0:
        # ★ 红线对应点：解码不了（如 moov 损坏）是"系统侧检测失败"，
        # 报 ANALYSIS_ERROR 让人来判断，而不是宣称"视频不合格"。
        raise DetectorError(f"ffprobe 退出码 {proc.returncode}: {proc.stderr[:200]}")

    try:
        data = json.loads(proc.stdout)
    except json.JSONDecodeError as e:
        raise DetectorError(f"ffprobe 输出不可解析: {e}") from e
    return parse_ffprobe_output(data)


def parse_ffprobe_output(data: dict) -> ProbeResult:
    """纯函数：解析 ffprobe JSON（单独抽出便于单测，不需要真视频）。"""
    fmt = data.get("format", {}) or {}
    duration_s = fmt.get("duration")
    duration_ms = int(float(duration_s) * 1000) if duration_s else None

    streams = data.get("streams", []) or []
    video = next((s for s in streams if s.get("codec_type") == "video"), None)
    audio = next((s for s in streams if s.get("codec_type") == "audio"), None)

    width = height = None
    fps = None
    if video:
        width, height = video.get("width"), video.get("height")
        # avg_frame_rate="0/0" 是常见的“无有效平均值”，字符串本身却为真；
        # 必须先解析，再决定是否回退 r_frame_rate。
        fps = _parse_rate(video.get("avg_frame_rate") or "0/0")
        if fps is None:
            fps = _parse_rate(video.get("r_frame_rate") or "0/0")
    return ProbeResult(duration_ms, width, height, fps, audio is not None)


def _parse_rate(rate: str) -> Optional[float]:
    try:
        num, _, den = rate.partition("/")
        den_i = int(den) if den else 1
        if den_i == 0:
            return None
        value = int(num) / den_i
        return round(value, 3) if value > 0 else None
    except (ValueError, ZeroDivisionError):
        return None
