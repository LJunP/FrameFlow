#!/usr/bin/env python3
"""F3-T5 可复现的合成测试视频生成器。

两种模式：
1. 本机装了 ffmpeg：生成真正可播放、可被 ffprobe 解析的测试视频
   （F4 质检流水线的深度探针需要这种）。
2. 没装 ffmpeg：退化为"仅容器签名"的占位文件（F3 入口校验级别够用：
   MP4 头部带 ftyp 即可通过 magic bytes 检查）。

用法示例：
    python3 scripts/gen_test_media.py --out local-pilot-data/test-media --count 3
    python3 scripts/gen_test_media.py --out /tmp/media --duration 8 --resolution 720x1280
"""

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

MP4_FTYP_POS = 4  # 'ftyp' 盒类型位于文件头第 4 字节起


def ffmpeg_available() -> bool:
    return shutil.which("ffmpeg") is not None


def gen_real(out: Path, duration: int, resolution: str, vertical: bool) -> Path:
    res = resolution or ("1080x1920" if vertical else "1920x1080")
    out.parent.mkdir(parents=True, exist_ok=True)
    cmd = [
        "ffmpeg", "-y",
        "-f", "lavfi", "-i", f"testsrc2=duration={duration}:rate=30:size={res}",
        "-f", "lavfi", "-i", f"sine=frequency=440:duration={duration}",
        "-c:v", "libx264", "-preset", "veryfast", "-pix_fmt", "yuv420p",
        "-c:a", "aac",
        str(out),
    ]
    subprocess.run(cmd, check=True, capture_output=True)
    return out


def gen_stub(out: Path, size: int) -> Path:
    out.parent.mkdir(parents=True, exist_ok=True)
    data = bytearray(size)
    data[MP4_FTYP_POS:MP4_FTYP_POS + 8] = b"ftypisom"
    out.write_bytes(bytes(data))
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out", required=True, help="输出目录")
    ap.add_argument("--count", type=int, default=3)
    ap.add_argument("--duration", type=int, default=8, help="每条秒数（ffmpeg 模式）")
    ap.add_argument("--resolution", default=None, help="如 720x1280（ffmpeg 模式）")
    ap.add_argument("--vertical", action="store_true", help="竖屏默认分辨率")
    ap.add_argument("--stub-size", type=int, default=1024, help="占位模式字节数")
    args = ap.parse_args()

    out_dir = Path(args.out)
    if ffmpeg_available():
        print("检测到 ffmpeg：生成真实可解析视频")
        for i in range(1, args.count + 1):
            f = gen_real(out_dir / f"test-{i:02d}.mp4", args.duration,
                         args.resolution, args.vertical)
            print(f"  {f}")
    else:
        print("未检测到 ffmpeg：生成仅容器签名的占位文件（可通过 F3 入口校验，"
              "不能通过 F4 ffprobe 深度探针）")
        for i in range(1, args.count + 1):
            f = gen_stub(out_dir / f"stub-{i:02d}.mp4", args.stub_size)
            print(f"  {f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
