from pathlib import Path

import pytest

from frameflow_ai.ffmpeg import resolve_toolchain
from frameflow_ai.media import aspect_ratio, probe


@pytest.mark.skipif(not Path(Path(__file__).resolve().parents[2] / "experiments/fixtures/generated").exists(),
                    reason="fixture dataset not generated")
def test_probe_normal_vertical_machine_readable(dataset):
    facts = probe(dataset / "normal_vertical.mp4")
    assert facts.has_video
    assert facts.has_audio
    assert facts.width == 360
    assert facts.height == 640
    assert aspect_ratio(facts.width, facts.height) == "9:16"


@pytest.mark.skipif(not Path(Path(__file__).resolve().parents[2] / "experiments/fixtures/generated").exists(),
                    reason="fixture dataset not generated")
def test_probe_wrong_aspect(dataset):
    facts = probe(dataset / "wrong_aspect.mp4")
    assert aspect_ratio(facts.width, facts.height) == "16:9"


@pytest.mark.skipif(not Path(Path(__file__).resolve().parents[2] / "experiments/fixtures/generated").exists(),
                    reason="fixture dataset not generated")
def test_probe_silent_has_no_audio(dataset):
    facts = probe(dataset / "silent_vertical.mp4")
    assert not facts.has_audio


def test_aspect_ratio_reduction():
    assert aspect_ratio(360, 640) == "9:16"
    assert aspect_ratio(1920, 1080) == "16:9"
