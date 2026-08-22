"""ffprobe 输出解析单测（纯函数，无需二进制）。"""

from frameflow_ai.detectors.probe import parse_ffprobe_output


def test_parse_typical_mp4():
    data = {
        "format": {"duration": "8.5"},
        "streams": [
            {"codec_type": "video", "width": 1080, "height": 1920,
             "avg_frame_rate": "30000/1001"},
            {"codec_type": "audio", "codec_name": "aac"},
        ],
    }
    r = parse_ffprobe_output(data)
    assert r.duration_ms == 8500
    assert (r.width, r.height) == (1080, 1920)
    assert abs(r.fps - 29.97) < 0.01
    assert r.has_audio


def test_parse_no_audio():
    data = {"format": {"duration": "3"}, "streams": [
        {"codec_type": "video", "width": 720, "height": 1280, "r_frame_rate": "30/1"}]}
    r = parse_ffprobe_output(data)
    assert not r.has_audio
    assert r.fps == 30.0


def test_parse_zero_denominator_is_none():
    data = {"format": {}, "streams": [
        {"codec_type": "video", "width": 0, "height": 0, "avg_frame_rate": "0/0"}]}
    r = parse_ffprobe_output(data)
    assert r.fps is None and r.duration_ms is None
