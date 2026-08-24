#!/usr/bin/env bash
set -euo pipefail
umask 077

environment=""
compose_file=""
env_file=""
manifest=""
state_dir=""
base_url=""
media_url=""
confirm=""
apply=false

usage() {
  echo "usage: $0 --environment ENV --compose-file FILE --env-file FILE --manifest FILE --state-dir /ABS/PATH/ENV --base-url URL [--media-url URL] [--apply --confirm PROMOTE_ENV]" >&2
}

while (($#)); do
  case "$1" in
    --environment) environment=${2:?}; shift 2 ;;
    --compose-file) compose_file=${2:?}; shift 2 ;;
    --env-file) env_file=${2:?}; shift 2 ;;
    --manifest) manifest=${2:?}; shift 2 ;;
    --state-dir) state_dir=${2:?}; shift 2 ;;
    --base-url) base_url=${2:?}; shift 2 ;;
    --media-url) media_url=${2:?}; shift 2 ;;
    --confirm) confirm=${2:?}; shift 2 ;;
    --apply) apply=true; shift ;;
    *) usage; exit 2 ;;
  esac
done

[[ $environment =~ ^(dev|staging|production)$ ]] || { usage; exit 2; }
[[ -f $compose_file && -f $env_file && -f $manifest ]] || { usage; exit 2; }
[[ $state_dir == /* && ${state_dir##*/} == "$environment" && $state_dir != "/$environment" ]] || {
  echo "state-dir must be an absolute, environment-scoped directory ending in /$environment" >&2; exit 2;
}

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
"$script_dir/check-env-isolation.sh" --environment "$environment" --env-file "$env_file"
"$script_dir/validate-image-manifest.sh" "$manifest"
release_sha=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["release"]["gitSha"])' "$manifest")
release_dir="$state_dir/releases/$release_sha"
expected_confirm="PROMOTE_$(printf '%s' "$environment" | tr '[:lower:]' '[:upper:]')"

echo "promotion plan:"
echo "  environment: $environment"
echo "  manifest:    $manifest"
echo "  release:     $release_sha"
echo "  state dir:   $state_dir"
echo "  public URL:  $base_url"
if ! $apply; then
  echo "DRY-RUN: no files, containers, network, or release pointers changed"
  exit 0
fi
[[ $confirm == "$expected_confirm" ]] || {
  echo "refusing apply: --confirm must equal $expected_confirm" >&2; exit 2;
}

mkdir -p "$state_dir/releases"
if [[ -e $release_dir ]]; then
  [[ -f $release_dir/image-manifest.json && ! -L $release_dir/image-manifest.json &&
     -f $release_dir/release.env && ! -L $release_dir/release.env ]] || {
    echo "release SHA directory is incomplete or contains symlinked state" >&2; exit 1;
  }
  cmp -s "$manifest" "$release_dir/image-manifest.json" || {
    echo "release SHA directory already exists with a different manifest" >&2; exit 1;
  }
else
  mkdir "$release_dir"
  install -m 0600 "$manifest" "$release_dir/image-manifest.json"
  release_env_staging=$(mktemp "$release_dir/.release.env.XXXXXX")
  trap 'rm -f "$release_env_staging"' EXIT
  "$script_dir/validate-image-manifest.sh" "$release_dir/image-manifest.json" \
    --expected-git-sha "$release_sha" --emit-env >"$release_env_staging"
  chmod 0600 "$release_env_staging"
  mv "$release_env_staging" "$release_dir/release.env"
fi

# ★ 核心：Compose 真正消费的是 release.env，不是 JSON manifest。每次晋级都从
# 已验证 manifest 重生期望值并逐字节比较，防止缓存的 env 漂移到另一组镜像 Digest。
cmp -s \
  <("$script_dir/validate-image-manifest.sh" "$release_dir/image-manifest.json" \
    --expected-git-sha "$release_sha" --emit-env) \
  "$release_dir/release.env" || {
    echo "release.env does not exactly match the validated image manifest" >&2
    exit 1
  }

old_release=""
if [[ -L $state_dir/current ]]; then
  old_release=$(readlink "$state_dir/current")
  [[ $old_release == releases/* && -f $state_dir/$old_release/release.env ]] || {
    echo "current release pointer escapes releases/ or is incomplete" >&2; exit 1;
  }
fi

compose=(docker compose --project-name "frameflow-$environment" --env-file "$env_file" --env-file "$release_dir/release.env" -f "$compose_file")
"${compose[@]}" pull
"${compose[@]}" up -d --remove-orphans --wait --wait-timeout 180
smoke=("$script_dir/smoke-test.sh" --environment "$environment" --compose-file "$compose_file" --env-file "$env_file" --image-env-file "$release_dir/release.env" --base-url "$base_url")
[[ -z $media_url ]] || smoke+=(--media-url "$media_url")

# ★ 核心：只有新镜像全栈 Smoke 通过才移动 current；失败时尽力恢复旧镜像。
# 若先改指针或忽略 Smoke，自动化会把不可服务版本登记成“当前成功版本”。
if ! "${smoke[@]}"; then
  if [[ -n $old_release ]]; then
    old_env="$state_dir/$old_release/release.env"
    echo "promotion failed; attempting image rollback to $old_release" >&2
    docker compose --project-name "frameflow-$environment" --env-file "$env_file" --env-file "$old_env" -f "$compose_file" up -d --remove-orphans --wait --wait-timeout 180 || true
  fi
  exit 1
fi

if [[ -n $old_release && $old_release != "releases/$release_sha" ]]; then
  ln -sfn "$old_release" "$state_dir/previous.new"
  mv -f "$state_dir/previous.new" "$state_dir/previous"
fi
ln -sfn "releases/$release_sha" "$state_dir/current.new"
mv -f "$state_dir/current.new" "$state_dir/current"
echo "promotion: PASS ($environment $release_sha)"
