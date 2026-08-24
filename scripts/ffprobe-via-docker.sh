#!/usr/bin/env bash
set -euo pipefail

if (( $# < 1 )); then
  echo "usage: ffprobe-via-docker.sh [ffprobe options] MEDIA_PATH" >&2
  exit 64
fi

arguments=("$@")
last_index=$(( ${#arguments[@]} - 1 ))
media_path=${arguments[$last_index]}
unset 'arguments[$last_index]'

if [[ ! -f "$media_path" ]]; then
  echo "ffprobe media does not exist: $media_path" >&2
  exit 66
fi

media_dir=$(cd "$(dirname "$media_path")" && pwd -P)
media_name=$(basename "$media_path")
ffmpeg_image=${FRAMEFLOW_TEST_FFMPEG_IMAGE:-linuxserver/ffmpeg:latest}

# ★ 核心：只读挂载单个媒体所在目录，并禁止自动拉取镜像；若这里挂载过宽或
# 允许隐式拉取，本地测试会扩大文件暴露面并引入不可复现的外部状态。
exec docker run --rm --pull=never \
  -v "$media_dir:/input:ro" \
  --entrypoint ffprobe \
  "$ffmpeg_image" \
  "${arguments[@]}" "/input/$media_name"
