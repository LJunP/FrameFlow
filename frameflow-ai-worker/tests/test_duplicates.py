from pathlib import Path

import pytest

from frameflow_ai.duplicates import fingerprint, hamming, sha256_of, similarity

GENERATED = Path(__file__).resolve().parents[2] / "experiments/fixtures/generated"


@pytest.mark.skipif(not GENERATED.exists(), reason="fixture dataset not generated")
def test_exact_duplicate_clusters_together():
    a = fingerprint(GENERATED / "duplicate_source.mp4")
    b = fingerprint(GENERATED / "duplicate_copy.mp4")
    assert a["sha256"] == b["sha256"]
    assert similarity(a, b) == 1.0


@pytest.mark.skipif(not GENERATED.exists(), reason="fixture dataset not generated")
def test_near_duplicate_similarity_below_exact():
    a = fingerprint(GENERATED / "duplicate_source.mp4")
    b = fingerprint(GENERATED / "near_duplicate.mp4")
    assert 0.0 <= similarity(a, b) < 1.0


def test_hamming_identity_and_difference():
    assert hamming(0b1111, 0b1111) == 0
    assert hamming(0b1111, 0b0000) == 4


def test_sha256_stable(tmp_path):
    p = tmp_path / "dup-test.bin"
    p.write_bytes(b"x" * 1024)
    assert sha256_of(p) == sha256_of(p)
