"""完整产品真实 Provider 门禁工具的离线测试；不会启动服务或联网。"""

import importlib.util
from pathlib import Path

import cv2


SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "run_real_provider_e2e_gate.py"
SPEC = importlib.util.spec_from_file_location("run_real_provider_e2e_gate", SCRIPT)
assert SPEC and SPEC.loader
gate = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(gate)


def test_full_e2e_brief_omits_visual_answer_and_selects_luna():
    challenge = "FF-E2E-TEST-CODE"
    brief = gate.build_brief()
    profile = gate.build_profile()

    assert challenge not in brief
    assert "VISUAL CHALLENGE" in brief
    assert profile["semantic"] == {
        "enabled": True,
        "modelId": "opencode-go-luna-v1",
        "dimensions": [
            "prompt_alignment",
            "quality_impression",
            "policy_violation",
        ],
    }


def test_full_e2e_generator_creates_real_moving_synthetic_mp4(tmp_path):
    output = tmp_path / "synthetic-e2e.mp4"

    metadata = gate.generate_synthetic_video(output, "FF-E2E-A1B2-C3D4")

    assert metadata["syntheticOnly"] is True
    assert metadata["hasCustomerData"] is False
    assert metadata["frameCount"] == 144
    assert metadata["fps"] == 24
    assert len(metadata["sha256"]) == 64
    capture = cv2.VideoCapture(str(output))
    try:
        assert capture.isOpened()
        assert int(capture.get(cv2.CAP_PROP_FRAME_WIDTH)) == 1280
        assert int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT)) == 720
        assert int(capture.get(cv2.CAP_PROP_FRAME_COUNT)) == 144
        frames = []
        for index in (0, 48, 96):
            capture.set(cv2.CAP_PROP_POS_FRAMES, index)
            ok, frame = capture.read()
            assert ok
            frames.append(frame)
    finally:
        capture.release()
    assert all(float(frame.mean()) > 16 for frame in frames)
    assert any((frames[index] != frames[index + 1]).any() for index in range(2))
