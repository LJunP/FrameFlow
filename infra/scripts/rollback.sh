#!/usr/bin/env bash
set -euo pipefail

environment=""
compose_file=""
env_file=""
state_dir=""
base_url=""
media_url=""
target=""
confirm=""
apply=false
ack_forward_only=false

usage() {
  echo "usage: $0 --environment ENV --compose-file FILE --env-file FILE --state-dir /ABS/PATH/ENV --base-url URL [--media-url URL] [--target GIT_SHA] [--apply --ack-forward-only-db --confirm ROLLBACK_ENV]" >&2
}

while (($#)); do
  case "$1" in
    --environment) environment=${2:?}; shift 2 ;;
    --compose-file) compose_file=${2:?}; shift 2 ;;
    --env-file) env_file=${2:?}; shift 2 ;;
    --state-dir) state_dir=${2:?}; shift 2 ;;
    --base-url) base_url=${2:?}; shift 2 ;;
    --media-url) media_url=${2:?}; shift 2 ;;
    --target) target=${2:?}; shift 2 ;;
    --confirm) confirm=${2:?}; shift 2 ;;
    --apply) apply=true; shift ;;
    --ack-forward-only-db) ack_forward_only=true; shift ;;
    *) usage; exit 2 ;;
  esac
done

[[ $environment =~ ^(dev|staging|production)$ ]] || { usage; exit 2; }
[[ -f $compose_file && -f $env_file ]] || { usage; exit 2; }
[[ $state_dir == /* && ${state_dir##*/} == "$environment" && $state_dir != "/$environment" ]] || {
  echo "invalid state-dir" >&2; exit 2;
}
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
"$script_dir/check-env-isolation.sh" --environment "$environment" --env-file "$env_file"

if [[ -z $target ]]; then
  [[ -L $state_dir/previous ]] || { echo "no previous release pointer" >&2; exit 1; }
  target_link=$(readlink "$state_dir/previous")
  [[ $target_link == releases/* ]] || { echo "previous pointer escapes releases/" >&2; exit 1; }
  target=${target_link#releases/}
fi
[[ $target =~ ^[0-9a-f]{40}$ ]] || { echo "target must be a full Git SHA" >&2; exit 2; }
target_dir="$state_dir/releases/$target"
[[ -f $target_dir/image-manifest.json && -f $target_dir/release.env ]] || {
  echo "target release is incomplete: $target_dir" >&2; exit 1;
}
[[ ! -L $target_dir/image-manifest.json && ! -L $target_dir/release.env ]] || {
  echo "target release state must not contain symlinks" >&2; exit 1;
}
"$script_dir/validate-image-manifest.sh" "$target_dir/image-manifest.json" --expected-git-sha "$target"
# release.env is only a Compose projection of the manifest. It is never an independent
# source of truth and must compare byte-for-byte before Docker sees it.
cmp -s \
  <("$script_dir/validate-image-manifest.sh" "$target_dir/image-manifest.json" \
    --expected-git-sha "$target" --emit-env) \
  "$target_dir/release.env" || {
    echo "target release.env does not exactly match the validated image manifest" >&2
    exit 1
  }

echo "rollback plan: $environment -> $target"
echo "NOTE: this rolls back images only; Flyway migrations remain forward-only."
if ! $apply; then
  echo "DRY-RUN: no containers or release pointers changed"
  exit 0
fi
expected_confirm="ROLLBACK_$(printf '%s' "$environment" | tr '[:lower:]' '[:upper:]')"
[[ $confirm == "$expected_confirm" && $ack_forward_only == true ]] || {
  echo "refusing apply: require --ack-forward-only-db --confirm $expected_confirm" >&2; exit 2;
}

current_link=""
[[ ! -L $state_dir/current ]] || current_link=$(readlink "$state_dir/current")
compose=(docker compose --project-name "frameflow-$environment" --env-file "$env_file" --env-file "$target_dir/release.env" -f "$compose_file")
"${compose[@]}" pull
"${compose[@]}" up -d --remove-orphans --wait --wait-timeout 180
smoke=("$script_dir/smoke-test.sh" --environment "$environment" --compose-file "$compose_file" --env-file "$env_file" --image-env-file "$target_dir/release.env" --base-url "$base_url")
[[ -z $media_url ]] || smoke+=(--media-url "$media_url")
"${smoke[@]}"

# ★ 核心：Smoke 通过后才原子切换指针；数据库绝不执行向后迁移。
# 若旧镜像不兼容新 schema，Smoke 会失败并阻止“假成功”，此时应前滚修复。
if [[ -n $current_link && $current_link != "releases/$target" ]]; then
  ln -sfn "$current_link" "$state_dir/previous.new"
  mv -f "$state_dir/previous.new" "$state_dir/previous"
fi
ln -sfn "releases/$target" "$state_dir/current.new"
mv -f "$state_dir/current.new" "$state_dir/current"
echo "rollback: PASS ($environment $target)"
