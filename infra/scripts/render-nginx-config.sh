#!/usr/bin/env bash
set -euo pipefail

environment=""
env_file=""
mode=""
output=""
confirm=""
apply=false

while (($#)); do
  case "$1" in
    --environment) environment=${2:?}; shift 2 ;;
    --env-file) env_file=${2:?}; shift 2 ;;
    --mode) mode=${2:?}; shift 2 ;;
    --output) output=${2:?}; shift 2 ;;
    --confirm) confirm=${2:?}; shift 2 ;;
    --apply) apply=true; shift ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done
[[ $environment =~ ^(dev|staging|production)$ && $mode =~ ^(bootstrap|https)$ && -f $env_file ]] || {
  echo "usage: $0 --environment ENV --env-file FILE --mode bootstrap|https [--output ABS_PATH --apply --confirm RENDER_NGINX_ENV]" >&2; exit 2;
}

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
repo_root=$(CDPATH= cd -- "$script_dir/../.." && pwd -P)
"$script_dir/check-env-isolation.sh" --environment "$environment" --env-file "$env_file"
template="$repo_root/infra/nginx/http-bootstrap.conf.template"
[[ $mode == bootstrap ]] || template="$repo_root/infra/nginx/https.conf.template"
rendered=$(mktemp)
backup=""
trap 'rm -f "$rendered"; [[ -z "$backup" ]] || rm -f "$backup"' EXIT

python3 - "$env_file" "$template" "$rendered" <<'PY'
import pathlib, re, sys
env_path, template_path, output_path = map(pathlib.Path, sys.argv[1:])
values = {}
for raw in env_path.read_text(encoding="utf-8").splitlines():
    line = raw.strip()
    if not line or line.startswith("#"):
        continue
    key, value = line.split("=", 1)
    values[key.strip()] = value.strip()
text = template_path.read_text(encoding="utf-8")
required = set(re.findall(r"\$\{([A-Z][A-Z0-9_]*)\}", text))
missing = sorted(key for key in required if not values.get(key))
if missing:
    raise SystemExit("missing nginx values: " + ", ".join(missing))
for key in required:
    text = text.replace("${" + key + "}", values[key])
if re.search(r"\$\{[A-Z][A-Z0-9_]*\}", text):
    raise SystemExit("unresolved nginx placeholder")
output_path.write_text(text, encoding="utf-8")
PY

if ! $apply; then
  cat "$rendered"
  echo "# DRY-RUN: no Nginx file or process changed" >&2
  exit 0
fi
expected="RENDER_NGINX_$(printf '%s' "$environment" | tr '[:lower:]' '[:upper:]')"
[[ $confirm == "$expected" && $output == /* && ${output##*/} == "frameflow-$environment.conf" ]] || {
  echo "refusing apply: require absolute --output ending frameflow-$environment.conf and --confirm $expected" >&2; exit 2;
}
if [[ -e $output ]]; then
  backup=$(mktemp)
  cp -p "$output" "$backup"
fi
install -m 0644 "$rendered" "$output"
if ! nginx -t; then
  if [[ -n $backup ]]; then cp -p "$backup" "$output"; else rm -f "$output"; fi
  echo "nginx config rejected; previous file restored" >&2
  exit 1
fi
systemctl reload nginx
echo "nginx config: PASS ($environment $mode)"
