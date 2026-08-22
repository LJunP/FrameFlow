"""黑帧 / 冻结检测器（OpenCV + numpy）。

设计要点：判定逻辑写成【纯函数】（输入帧数组），与"从视频取帧"分离——
纯函数部分不需要视频文件即可单测，采帧部分很薄。
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Optional

import numpy as np

DETECTOR_ID = "opencv-frames"
DETECTOR_VERSION = "1"

# 一帧内亮度均值低于该值（0-255）视为黑帧
BLACK_LUMA_THRESHOLD = 16.0
# 相邻两帧的平均绝对差低于该值视为"冻结"（无运动）
FREEZE_DIFF_THRESHOLD = 2.0


@dataclass(frozen=True)
class FrameSegment:
    """一段连续异常的时间区间（含起点，单位 ms）。"""
    start_ms: int
    end_ms: int
    frames: int


def find_black_segments(frames: list[np.ndarray], timestamps_ms: list[int],
                        threshold: float = BLACK_LUMA_THRESHOLD) -> list[FrameSegment]:
    """找出连续黑帧区间。frames 为灰度化的二维 uint8 数组。"""
    return _segments_where(
        [float(f.mean()) < threshold for f in frames], timestamps_ms)


def find_freeze_segments(frames: list[np.ndarray], timestamps_ms: list[int],
                         diff_threshold: float = FREEZE_DIFF_THRESHOLD,
                         min_consecutive: int = 3) -> list[FrameSegment]:
    """找出连续"无变化"帧区间（≥ min_consecutive 帧才算冻结）。"""
    if len(frames) < min_consecutive + 1:
        return []
    still_flags: list[bool] = [False]  # 第 0 帧没有前帧，占位
    for prev, cur in zip(frames, frames[1:]):
        # 平均绝对差：对压缩噪声不敏感的"粗粒度运动度量"
        diff = float(np.abs(cur.astype(np.int16) - prev.astype(np.int16)).mean())
        still_flags.append(diff < diff_threshold)
    segments = _segments_where(still_flags, timestamps_ms)
    return [s for s in segments if s.frames >= min_consecutive]


def _segments_where(flags: list[bool], timestamps_ms: list[int]) -> list[FrameSegment]:
    """把布尔序列折叠成连续 True 区间（通用小工具）。"""
    segments: list[FrameSegment] = []
    start: Optional[int] = None
    for i, flag in enumerate(flags):
        if flag and start is None:
            start = i
        elif not flag and start is not None:
            segments.append(FrameSegment(timestamps_ms[start], timestamps_ms[i - 1], i - start))
            start = None
    if start is not None:
        segments.append(FrameSegment(timestamps_ms[start], timestamps_ms[-1], len(flags) - start))
    return segments


def sample_frames(path: str, max_frames: int = 300) -> tuple[list[np.ndarray], list[int]]:
    """用 OpenCV 均匀抽帧并转灰度。抽不出任何帧 → 抛 DetectorError 由上层转 ANALYSIS_ERROR。"""
    import cv2
    from .probe import DetectorError

    cap = cv2.VideoCapture(path)
    if not cap.isOpened():
        raise DetectorError(f"OpenCV 无法打开视频: {path}")
    try:
        total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
        step = max(1, total // max_frames)
        frames, stamps = [], []
        index = 0
        while len(frames) < max_frames:
            cap.set(cv2.CAP_PROP_POS_FRAMES, index)
            ok, frame = cap.read()
            if not ok:
                break
            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            frames.append(gray)
            stamps.append(int(index * 1000 / fps))
            index += step
        return frames, stamps
    finally:
        cap.release()
