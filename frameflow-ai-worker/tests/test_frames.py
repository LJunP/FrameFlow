"""黑帧/冻结检测器单测：合成 numpy 帧数组，不需要真实视频文件。"""

import numpy as np
import pytest

from frameflow_ai.detectors import frames
from frameflow_ai.detectors.probe import DetectorError


def _gray(value: int, size: int = 64) -> np.ndarray:
    return np.full((size, size), value, dtype=np.uint8)


def test_black_segments_found():
    # 亮度 5,5,5 是黑；128 正常；再一段黑
    seq = [_gray(5), _gray(5), _gray(5), _gray(128), _gray(5), _gray(5)]
    stamps = [0, 100, 200, 300, 400, 500]
    segments = frames.find_black_segments(seq, stamps)
    assert [(s.start_ms, s.end_ms, s.frames) for s in segments] == [
        (0, 200, 3), (400, 500, 2)]


def test_no_black_in_normal_video():
    seq = [_gray(120 + i) for i in range(10)]
    assert frames.find_black_segments(seq, list(range(0, 1000, 100))) == []


def test_freeze_detected_for_identical_frames():
    # 前 5 帧完全相同（冻结），随后出现运动
    frozen = [_gray(100)] * 5
    moving = frozen + [_gray(100), _gray(180), _gray(60), _gray(200), _gray(40)]
    stamps = list(range(0, len(moving) * 100, 100))
    segments = frames.find_freeze_segments(moving, stamps)
    assert len(segments) >= 1
    assert segments[0].frames >= 5


def test_freeze_not_flagged_for_moving_video():
    rng = np.random.default_rng(42)
    moving = [rng.integers(0, 255, (32, 32)).astype(np.uint8) for _ in range(20)]
    stamps = list(range(0, 2000, 100))
    assert frames.find_freeze_segments(moving, stamps) == []


def test_boundary_luma_threshold():
    # 恰好在阈值附近：<16 黑，>=16 不算
    seq = [_gray(15), _gray(16)]
    segments = frames.find_black_segments(seq, [0, 100])
    assert len(segments) == 1 and segments[0].frames == 1


def test_opened_video_with_no_decodable_frame_is_detector_error(monkeypatch):
    class EmptyCapture:
        def isOpened(self):
            return True

        def get(self, _key):
            return 0

        def set(self, _key, _value):
            return True

        def read(self):
            return False, None

        def release(self):
            return None

    import cv2
    monkeypatch.setattr(cv2, "VideoCapture", lambda _path: EmptyCapture())

    with pytest.raises(DetectorError, match="任何帧"):
        frames.sample_frames("empty.mp4")
