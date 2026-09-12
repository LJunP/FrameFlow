#!/usr/bin/env bash
# 宿主开发入口；完整运行优先使用 README 中的 Compose。中间件连接须由环境变量配置。
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/frameflow-ai-worker"
[[ -x .venv/bin/python ]] || { echo "先按 README 初始化 Worker .venv" >&2; exit 1; }
# ★ 核心：不依赖旧会话遗留的 .tmp/pre-f9-bin；无原生 ffprobe 时显式检查 Docker 镜像再生成入口。
if ! command -v ffprobe >/dev/null 2>&1; then
  image=${FRAMEFLOW_TEST_FFMPEG_IMAGE:-linuxserver/ffmpeg@sha256:771895205f3a62023f14e5ca1fe94be8007ecaf8a7c268d9c502de260c213d22}
  docker image inspect "$image" >/dev/null 2>&1 || {
    echo "缺少 ffprobe 与本地测试镜像；请安装 FFmpeg 或使用完整 Compose" >&2; exit 1;
  }
  mkdir -p "$ROOT/.tmp/demo-worker-bin"
  ln -sfn "$ROOT/scripts/ffprobe-via-docker.sh" "$ROOT/.tmp/demo-worker-bin/ffprobe"
  export PATH="$ROOT/.tmp/demo-worker-bin:$PATH"
fi
exec .venv/bin/python -m frameflow_ai
