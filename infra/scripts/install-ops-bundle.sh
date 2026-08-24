#!/usr/bin/env bash
set -euo pipefail
umask 027

source_root=""
bundle_root=""
manifest=""
confirm=""
apply=false
ops_root=/opt/frameflow/ops

usage() {
  echo "usage: $0 (--source-root ABS | --bundle-root ABS) --manifest FILE [--apply --confirm INSTALL_OPS_<12_SHA>]" >&2
}

while (($#)); do
  case "$1" in
    --source-root) source_root=${2:?}; shift 2 ;;
    --bundle-root) bundle_root=${2:?}; shift 2 ;;
    --manifest) manifest=${2:?}; shift 2 ;;
    --confirm) confirm=${2:?}; shift 2 ;;
    --apply) apply=true; shift ;;
    *) usage; exit 2 ;;
  esac
done

[[ -n $source_root && -z $bundle_root || -z $source_root && -n $bundle_root ]] || {
  echo "exactly one of --source-root or --bundle-root is required" >&2; exit 2;
}
if [[ -n $source_root ]]; then
  [[ $source_root == /* && -d $source_root && ! -L $source_root ]] || {
    echo "source-root must be an absolute non-symlink directory" >&2; exit 2;
  }
  source_root=$(cd "$source_root" && pwd -P)
  [[ -f $source_root/pom.xml && -f $source_root/infra/scripts/validate-image-manifest.sh ]] || {
    echo "source-root is not a FrameFlow Select checkout" >&2; exit 2;
  }
  validator="$source_root/infra/scripts/validate-image-manifest.sh"
else
  [[ $bundle_root == /* && -d $bundle_root && ! -L $bundle_root ]] || {
    echo "bundle-root must be an absolute non-symlink directory" >&2; exit 2;
  }
  bundle_root=$(cd "$bundle_root" && pwd -P)
  [[ -f $bundle_root/infra/scripts/validate-image-manifest.sh &&
     -f $bundle_root/OPS_GIT_SHA && ! -L $bundle_root/OPS_GIT_SHA &&
     -f $bundle_root/OPS_SHA256SUMS && ! -L $bundle_root/OPS_SHA256SUMS ]] || {
    echo "bundle-root is missing the manifest validator or OPS binding files" >&2; exit 2;
  }
  validator="$bundle_root/infra/scripts/validate-image-manifest.sh"
fi
[[ -f $manifest && ! -L $manifest ]] || { echo "manifest must be a regular non-symlink file" >&2; exit 2; }
manifest=$(cd "$(dirname "$manifest")" && printf '%s/%s\n' "$(pwd -P)" "$(basename "$manifest")")
"$validator" "$manifest"
git_sha=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["release"]["gitSha"])' "$manifest")
[[ $git_sha =~ ^[0-9a-f]{40}$ ]] || { echo "manifest Git SHA is invalid" >&2; exit 2; }

verify_bundle() {
  local root="$1"
  [[ -d $root/infra && ! -L $root/infra && -f $root/OPS_GIT_SHA && ! -L $root/OPS_GIT_SHA &&
     -f $root/OPS_SHA256SUMS && ! -L $root/OPS_SHA256SUMS ]] || return 1
  [[ $(<"$root/OPS_GIT_SHA") == "$git_sha" ]] || return 1
  [[ -z $(find "$root/infra" ! -type d ! -type f -print -quit) ]] || return 1
  python3 - "$root/OPS_SHA256SUMS" <<'PY' || return 1
import pathlib
import re
import sys

checksum_file = pathlib.Path(sys.argv[1])
paths = []
for number, line in enumerate(checksum_file.read_text(encoding="utf-8").splitlines(), 1):
    match = re.fullmatch(r"([0-9a-f]{64})  (infra/[^\\\r\n]+)", line)
    if not match:
        raise SystemExit(f"invalid OPS checksum line {number}")
    relative = pathlib.PurePosixPath(match.group(2))
    if relative.is_absolute() or ".." in relative.parts or "." in relative.parts:
        raise SystemExit(f"unsafe OPS checksum path on line {number}")
    paths.append(relative.as_posix())
if not paths or len(paths) != len(set(paths)):
    raise SystemExit("OPS checksum paths must be non-empty and unique")
PY
  (
    cd "$root"
    # ★ 核心：本函数会被 `if`/`||` 调用，Bash 会在该上下文关闭继承的 errexit；
    # checksum 失败必须显式终止，不能让后面的 file-set diff 把返回码覆盖成 0。
    sha256sum --quiet -c OPS_SHA256SUMS || exit 1
    diff -u \
      <(cut -c67- OPS_SHA256SUMS | LC_ALL=C sort) \
      <(find infra -type f -print | LC_ALL=C sort)
  ) >/dev/null
}

if [[ -n $source_root ]]; then
  git -C "$source_root" cat-file -e "$git_sha^{commit}" 2>/dev/null || {
    echo "source checkout does not contain manifest commit $git_sha" >&2; exit 2;
  }
  source_description="Git-tracked infra/ bytes from the manifest commit"
else
  verify_bundle "$bundle_root" || {
    echo "handoff bundle failed Git SHA, file-set, or checksum verification" >&2; exit 2;
  }
  source_description="prebuilt handoff bytes bound by OPS_GIT_SHA and OPS_SHA256SUMS"
fi
expected_confirm="INSTALL_OPS_${git_sha:0:12}"

echo "ops bundle plan: Git $git_sha -> $ops_root/releases/$git_sha"
echo "source: $source_description"
if [[ $apply != true ]]; then
  echo "DRY-RUN: no /opt files or current pointer changed"
  exit 0
fi
[[ ${EUID:-$(id -u)} -eq 0 ]] || { echo "ops bundle apply must run as root" >&2; exit 2; }
[[ $confirm == "$expected_confirm" ]] || {
  echo "refusing apply: --confirm must equal $expected_confirm" >&2; exit 2;
}
getent group frameflow >/dev/null || { echo "required group frameflow does not exist" >&2; exit 2; }
[[ ! -e $ops_root/current || -L $ops_root/current ]] || {
  echo "$ops_root/current must be absent or a symlink" >&2; exit 2;
}

install -d -m 0750 -o root -g frameflow "$ops_root" "$ops_root/releases"
release_dir="$ops_root/releases/$git_sha"
temporary=""
link_staging=""
cleanup() {
  if [[ -n $link_staging && -L $link_staging ]]; then
    unlink "$link_staging"
  fi
  if [[ -n $temporary && -d $temporary && ! -L $temporary && $temporary == "$ops_root"/.ops-bundle.* ]]; then
    find "$temporary" -type l -delete
    find "$temporary" -type f -delete
    find "$temporary" -depth -type d -empty -delete
  fi
}
trap cleanup EXIT

if [[ -e $release_dir ]]; then
  [[ -d $release_dir && ! -L $release_dir ]] || {
    echo "existing ops release is not a regular directory" >&2; exit 1;
  }
  verify_bundle "$release_dir" || {
    echo "existing ops release failed exact checksum verification" >&2; exit 1;
  }
else
  temporary=$(mktemp -d "$ops_root/.ops-bundle.${git_sha:0:12}.XXXXXX")
  if [[ -n $source_root ]]; then
    git -C "$source_root" archive --format=tar "$git_sha" infra | tar -xf - -C "$temporary"
    printf '%s\n' "$git_sha" >"$temporary/OPS_GIT_SHA"
    (
      cd "$temporary"
      find infra -type f -print0 | LC_ALL=C sort -z | xargs -0 sha256sum >OPS_SHA256SUMS
    )
  else
    cp -R "$bundle_root/infra" "$temporary/"
    cp "$bundle_root/OPS_GIT_SHA" "$bundle_root/OPS_SHA256SUMS" "$temporary/"
  fi
  [[ -d $temporary/infra && -z $(find "$temporary/infra" ! -type d ! -type f -print -quit) ]] || {
    echo "ops archive is missing or contains symlinks" >&2; exit 1;
  }
  find "$temporary" -type d -exec chmod 0750 {} +
  find "$temporary" -type f ! -perm /111 -exec chmod 0640 {} +
  find "$temporary" -type f -perm /111 -exec chmod 0750 {} +
  chown -R root:frameflow "$temporary"
  verify_bundle "$temporary" || { echo "new ops bundle failed verification" >&2; exit 1; }
  mv "$temporary" "$release_dir"
  temporary=""
fi

link_staging="$ops_root/.current.${git_sha:0:12}.$$"
ln -s "releases/$git_sha" "$link_staging"
mv -Tf "$link_staging" "$ops_root/current"
link_staging=""
echo "ops bundle install: PASS ($git_sha)"
