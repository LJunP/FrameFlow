#!/usr/bin/env bash
set -euo pipefail

catalog_file=""
output_file=""
replace=false
confirm=""
while (($#)); do
  case "$1" in
    --catalog) catalog_file=${2:?}; shift 2 ;;
    --output) output_file=${2:?}; shift 2 ;;
    --replace) replace=true; shift ;;
    --confirm) confirm=${2:?}; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

if [[ -z $catalog_file || -z $output_file || $output_file != /* ]]; then
  echo "usage: $0 --catalog FILE --output /ABS/PATH/worker-provider.env [--replace --confirm REPLACE_PROVIDER_SECRETS]" >&2
  exit 2
fi
if [[ ! -f $catalog_file || -L $catalog_file ]]; then
  echo "Provider secret capture: FAIL: catalog must be a regular non-symlink file" >&2
  exit 1
fi

output_dir=${output_file%/*}
[[ -n $output_dir ]] || output_dir=/
if [[ ! -d $output_dir || -L $output_dir ]]; then
  echo "Provider secret capture: FAIL: output parent must be an existing non-symlink directory" >&2
  exit 1
fi
if [[ -e $output_file || -L $output_file ]]; then
  if [[ $replace != true || $confirm != REPLACE_PROVIDER_SECRETS ]]; then
    echo "Provider secret capture: FAIL: output exists; replacement requires exact confirmation" >&2
    exit 1
  fi
  if [[ ! -f $output_file || -L $output_file ]]; then
    echo "Provider secret capture: FAIL: existing output is not a regular file" >&2
    exit 1
  fi
fi

names_file=$(mktemp "${TMPDIR:-/tmp}/frameflow-provider-names.XXXXXX")
staging_file=""
cleanup() {
  rm -f -- "$names_file"
  [[ -z $staging_file ]] || rm -f -- "$staging_file"
}
trap cleanup EXIT HUP INT TERM

# 这里只读取无密钥目录并输出变量名；JSON 原文与任何 Key 都不会进入日志。
python3 - "$catalog_file" >"$names_file" <<'PY'
import json, pathlib, re, sys

try:
    catalog = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError) as exc:
    raise SystemExit(f"Provider secret capture: FAIL: invalid catalog: {type(exc).__name__}")
if not isinstance(catalog, dict) or set(catalog) != {"defaultModelId", "models"}:
    raise SystemExit("Provider secret capture: FAIL: catalog root contract mismatch")
models = catalog.get("models")
if not isinstance(models, list) or not models:
    raise SystemExit("Provider secret capture: FAIL: catalog models must be non-empty")
names = []
for index, model in enumerate(models):
    if not isinstance(model, dict) or not isinstance(model.get("enabled"), bool):
        raise SystemExit(f"Provider secret capture: FAIL: invalid model at index {index}")
    if not model["enabled"]:
        continue
    name = model.get("apiKeyEnv")
    if not isinstance(name, str) or not re.fullmatch(r"[A-Z_][A-Z0-9_]*", name):
        raise SystemExit(f"Provider secret capture: FAIL: invalid apiKeyEnv at index {index}")
    if name not in names:
        names.append(name)
if not names:
    raise SystemExit("Provider secret capture: FAIL: no enabled model keys")
print("\n".join(names))
PY

umask 077
staging_file=$(mktemp "$output_dir/.worker-provider.env.tmp.XXXXXX")
key_count=0
# 循环本身从 names_file 读取变量名，单独保留 fd 3 读取操作者的隐藏输入。
exec 3<&0
while IFS= read -r env_name; do
  [[ -n $env_name ]] || continue
  printf '%s: ' "$env_name" >&2
  IFS= read -r -s secret_value <&3 || {
    printf '\nProvider secret capture: FAIL: input ended early\n' >&2
    exit 1
  }
  printf '\n' >&2
  if [[ ! $secret_value =~ ^[A-Za-z0-9._~+/=:-]{16,}$ ]]; then
    echo "Provider secret capture: FAIL: token must be at least 16 safe characters" >&2
    exit 1
  fi
  printf '%s=%s\n' "$env_name" "$secret_value" >>"$staging_file"
  secret_value=""
  key_count=$((key_count + 1))
done <"$names_file"

chmod 0600 "$staging_file"
mv -f -- "$staging_file" "$output_file"
staging_file=""
echo "Provider secret capture: PASS: wrote $key_count key name(s) to $output_file (mode 0600; values not echoed)"
