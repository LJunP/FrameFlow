#!/usr/bin/env python3
"""F3-T5 可复现的合成测试视频生成器。

真实媒体优先使用本机 ffmpeg；本机没有时可复用已经存在的 Docker FFmpeg
镜像。只有两者都不可用且未指定 ``--require-real`` 时，才退化为仅容器
签名的占位文件。

用法示例：
    python3 scripts/gen_test_media.py --out local-pilot-data/test-media --count 3
    python3 scripts/gen_test_media.py --out /tmp/media --duration 8 --resolution 720x1280
"""

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

MP4_FTYP_POS = 4  # 'ftyp' 盒类型位于文件头第 4 字节起


DEFAULT_FFMPEG_IMAGE = "linuxserver/ffmpeg:latest"


def docker_image_available(image: str) -> bool:
    if shutil.which("docker") is None:
        return False
    probe = subprocess.run(
        ["docker", "image", "inspect", image], capture_output=True, check=False)
    return probe.returncode == 0


def resolve_ffmpeg(image: str) -> str | None:
    if shutil.which("ffmpeg") is not None:
        return "host"
    if docker_image_available(image):
        return "docker"
    return None


def run_ffmpeg(args: list[str], out: Path, runner: str, image: str) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    if runner == "host":
        cmd = ["ffmpeg", *args, str(out)]
    else:
        # ★ 核心：Docker 只挂载明确的输出目录，并把输出文件名映射到容器内；
        # 若直接传宿主绝对路径，容器会写到自己的临时文件系统，测试会虚假成功。
        cmd = [
            "docker", "run", "--rm",
            "-v", f"{out.parent.resolve()}:/output",
            "--entrypoint", "ffmpeg", image,
            *args, f"/output/{out.name}",
        ]
    subprocess.run(cmd, check=True, capture_output=True)


def gen_real(out: Path, duration: int, resolution: str, vertical: bool,
             runner: str, image: str, source: str = "testsrc2",
             fps: int = 30, tone_hz: int = 440,
             video_filter: str | None = None) -> Path:
    res = resolution or ("1080x1920" if vertical else "1920x1080")
    args = [
        "-y",
        "-f", "lavfi", "-i", f"{source}=duration={duration}:rate={fps}:size={res}",
        "-f", "lavfi", "-i", f"sine=frequency={tone_hz}:duration={duration}",
    ]
    if video_filter:
        args.extend(["-vf", video_filter])
    args.extend([
        "-c:v", "libx264", "-preset", "veryfast", "-pix_fmt", "yuv420p",
        "-c:a", "aac", "-movflags", "+faststart",
    ])
    run_ffmpeg(args, out, runner, image)
    return out


def gen_pre_f9_suite(out_dir: Path, runner: str, image: str) -> list[Path]:
    """生成覆盖 PASS、精确重复、另一合格素材、确定性淘汰和系统错误的夹具。"""
    valid_a = gen_real(out_dir / "valid-motion-a.mp4", 6, "640x360", False,
                       runner, image, source="testsrc2", fps=24, tone_hz=440)
    duplicate = out_dir / "valid-motion-a-exact-copy.mp4"
    shutil.copyfile(valid_a, duplicate)
    valid_b = gen_real(out_dir / "valid-motion-b.mp4", 6, "640x360", False,
                       runner, image, source="testsrc2", fps=30, tone_hz=660,
                       video_filter="hflip,eq=contrast=1.2")
    too_short = gen_real(out_dir / "too-short.mp4", 1, "640x360", False,
                         runner, image, source="testsrc2", fps=24, tone_hz=880)
    invalid = gen_stub(out_dir / "invalid-container.mp4", 2048)
    return [valid_a, duplicate, valid_b, too_short, invalid]


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
    ap.add_argument("--suite", choices=["basic", "pre-f9"], default="basic",
                    help="pre-f9 会生成覆盖完整正确性门禁的五类媒体")
    ap.add_argument("--ffmpeg-image",
                    default=os.environ.get("FRAMEFLOW_TEST_FFMPEG_IMAGE", DEFAULT_FFMPEG_IMAGE),
                    help="本机无 ffmpeg 时复用的本地 Docker 镜像")
    ap.add_argument("--require-real", action="store_true",
                    help="没有真实 ffmpeg 执行器时失败，禁止退化为占位文件")
    args = ap.parse_args()

    out_dir = Path(args.out)
    runner = resolve_ffmpeg(args.ffmpeg_image)
    if runner:
        print(f"使用 {runner} ffmpeg：生成真实可解析视频")
        if args.suite == "pre-f9":
            generated = gen_pre_f9_suite(out_dir, runner, args.ffmpeg_image)
        else:
            generated = [
                gen_real(out_dir / f"test-{i:02d}.mp4", args.duration,
                         args.resolution, args.vertical, runner, args.ffmpeg_image)
                for i in range(1, args.count + 1)
            ]
        for f in generated:
            print(f"  {f}")
    else:
        if args.require_real:
            print("错误：本机 ffmpeg 与指定的本地 Docker 镜像都不可用", file=sys.stderr)
            return 2
        print("未检测到 ffmpeg：生成仅容器签名的占位文件（可通过 F3 入口校验，"
              "不能通过 F4 ffprobe 深度探针）")
        for i in range(1, args.count + 1):
            f = gen_stub(out_dir / f"stub-{i:02d}.mp4", args.stub_size)
            print(f"  {f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
