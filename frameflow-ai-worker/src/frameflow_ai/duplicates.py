"""Duplicate detection: exact file digests and perceptual keyframe hashes."""
from __future__ import annotations

import hashlib
import subprocess
from pathlib import Path

from PIL import Image

from .ffmpeg import resolve_toolchain


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def compute_average_hash(image: Image.Image, bits: int = 64) -> int:
    """dHash/variant: resize to gray, compute row differences."""
    img = image.convert("L").resize((32, 32), Image.LANCZOS)
    pixels = list(img.getdata())
    diff = 0
    index = 0
    for row in range(32):
        for col in range(32):
            left = pixels[row * 32 + col]
            right = pixels[row * 32 + col + 1] if col + 1 < 32 else 0
            diff = (diff << 1) | (1 if left > right else 0)
            index += 1
            if index >= bits:
                return diff
    return diff


def extract_representative_frame(video_path: Path, tc, time_s: float = 0.5) -> Image.Image:
    proc = subprocess.run(
        [tc.ffmpeg, "-hide_banner", "-loglevel", "error", "-ss", str(time_s), "-i", str(video_path),
         "-frames:v", "1", "-f", "image2pipe", "-vcodec", "png", "-"],
        capture_output=True,
    )
    if proc.returncode != 0:
        raise RuntimeError(f"frame extraction failed: {proc.stderr[:200]}")
    import io
    return Image.open(io.BytesIO(proc.stdout)).convert("RGB")


def keyframe_hashes(video_path: Path, tc, sample_count: int = 6) -> list[int]:
    """Perceptual hashes at evenly spaced sample times (deterministic)."""
    try:
        from .media import probe
        facts = probe(video_path, tc)
    except Exception:
        return []
    duration = facts.duration_seconds or 0.0
    if duration <= 0:
        return []
    hashes = []
    for i in range(sample_count):
        t = (i + 0.5) / sample_count * duration
        try:
            img = extract_representative_frame(video_path, tc, t)
            hashes.append(compute_average_hash(img))
        except Exception:
            continue
    return hashes


def hamming(a: int, b: int) -> int:
    return (a ^ b).bit_count()


def fingerprint(video_path: Path, tc=None) -> dict:
    """Deterministic fingerprint: sha256 + perceptual hash vector."""
    tc = tc or resolve_toolchain()
    return {
        "sha256": sha256_of(video_path),
        "perceptualHashes": keyframe_hashes(video_path, tc),
    }


def similarity(a: dict, b: dict) -> float:
    """Similarity in [0,1]: 1.0 for exact (md5/sha256 equal); otherwise the
    mean of each frame's best contiguous-match hamming similarity."""
    if a.get("sha256") and a["sha256"] == b.get("sha256"):
        return 1.0
    ha = a.get("perceptualHashes") or []
    hb = b.get("perceptualHashes") or []
    if not ha or not hb:
        return 0.0
    return _best_match(ha, hb)


def _best_match(ha: list[int], hb: list[int]) -> float:
    total = 0.0
    count = 0
    for a in ha:
        best = max(((64 - hamming(a, b)) / 64.0) for b in hb)
        total += best
        count += 1
    return total / count if count else 0.0

def cluster(fingerprints: dict[str, dict], threshold: float = 0.85) -> dict[str, list[str]]:
    """Greedy single-link clustering by similarity threshold. Exact duplicates
    (similarity 1.0) always land in the same cluster. Deterministic order by key."""
    ordered = sorted(fingerprints)
    clusters: list[set[str]] = []
    for key in ordered:
        fp = fingerprints[key]
        placed = False
        for cluster in clusters:
            rep = next(iter(cluster))
            if similarity(fp, fingerprints[rep]) >= threshold:
                cluster.add(key)
                placed = True
                break
        if not placed:
            clusters.append({key})
    return {f"cluster-{i+1}": sorted(c) for i, c in enumerate(clusters)}
