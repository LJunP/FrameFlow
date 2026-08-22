"""感知哈希（dHash 8x8 → 64bit）与内容指纹。

★ 为什么用 dHash 而不是 pHash：dHash（相邻像素差分）实现 10 行、
无 DCT 依赖，对"几乎相同的画面"区分度足够；近重复检测要的就是
"压缩/微裁剪后哈希仍接近"，dHash 完全胜任且快一个量级。
"""

from __future__ import annotations

import hashlib
from typing import Optional

import numpy as np


def dhash(gray: np.ndarray) -> Optional[str]:
    """输入单帧灰度图，输出 16 位十六进制（64bit）dHash；空帧返回 None。"""
    if gray is None or gray.size == 0:
        return None
    import cv2
    # 9x8：每行 8 次相邻比较 → 8x8=64 bit
    small = cv2.resize(gray, (9, 8), interpolation=cv2.INTER_AREA)
    bits = small[:, 1:] > small[:, :-1]          # 右 > 左 → 1
    value = 0
    for row in bits:
        for b in row:
            value = (value << 1) | int(b)
    return format(value, "016x")


def file_sha256(path: str) -> str:
    """流式计算文件 SHA-256（精确重复指纹）。"""
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()
