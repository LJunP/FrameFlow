"""感知哈希（dHash）与内容指纹测试。"""

import numpy as np

from frameflow_ai.detectors.phash import dhash, file_sha256


def _gray(value, size=64):
    return np.full((size, size), value, dtype=np.uint8)


def test_identical_frames_same_hash():
    a = _gray(128, 256)
    b = _gray(128, 256)
    assert dhash(a) == dhash(b) and len(dhash(a)) == 16


def test_gradient_vs_flat_differ():
    flat = _gray(128)
    gradient = np.tile(np.linspace(0, 255, 64, dtype=np.uint8), (64, 1))
    ha, hb = dhash(flat), dhash(gradient)
    assert ha != hb


def test_near_identical_frames_small_hamming_distance():
    base = np.tile(np.linspace(0, 255, 64, dtype=np.uint8), (64, 1))
    noisy = base.copy()
    noisy[0, :8] = 255   # 微小扰动（模拟再压缩/微裁剪）
    ha, hb = dhash(base), dhash(noisy)
    distance = bin(int(ha, 16) ^ int(hb, 16)).count("1")
    assert distance <= 4   # 近重复应落在阈值内


def test_sha256_known_vector(tmp_path):
    f = tmp_path / "x.bin"
    f.write_bytes(b"hello frameflow")
    assert file_sha256(str(f)) == __import__("hashlib").sha256(b"hello frameflow").hexdigest()
