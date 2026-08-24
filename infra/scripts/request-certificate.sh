#!/usr/bin/env bash
set -euo pipefail

environment=""
env_file=""
confirm=""
apply=false
webroot=/var/www/certbot
while (($#)); do
  case "$1" in
    --environment) environment=${2:?}; shift 2 ;;
    --env-file) env_file=${2:?}; shift 2 ;;
    --webroot) webroot=${2:?}; shift 2 ;;
    --confirm) confirm=${2:?}; shift 2 ;;
    --apply) apply=true; shift ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done
[[ $environment =~ ^(dev|staging|production)$ && -f $env_file && $webroot == /* ]] || {
  echo "usage: $0 --environment ENV --env-file FILE [--webroot ABS] [--apply --confirm ISSUE_CERTIFICATE_ENV]" >&2; exit 2;
}
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
"$script_dir/check-env-isolation.sh" --environment "$environment" --env-file "$env_file"
read_key() {
  python3 - "$env_file" "$1" <<'PY'
import pathlib, sys
for raw in pathlib.Path(sys.argv[1]).read_text(encoding="utf-8").splitlines():
    if raw.strip() and not raw.lstrip().startswith("#") and raw.split("=", 1)[0].strip() == sys.argv[2]:
        print(raw.split("=", 1)[1].strip()); raise SystemExit
raise SystemExit("missing key: " + sys.argv[2])
PY
}
domain=$(read_key FRAMEFLOW_DOMAIN)
media_domain=$(read_key FRAMEFLOW_MEDIA_DOMAIN)
email=$(read_key FRAMEFLOW_CERTBOT_EMAIL)
command=(certbot certonly --non-interactive --agree-tos --no-eff-email --webroot -w "$webroot" -m "$email" -d "$domain" -d "$media_domain")
printf 'certificate plan:'; printf ' %q' "${command[@]}"; echo
if ! $apply; then echo "DRY-RUN: no ACME request sent"; exit 0; fi
expected="ISSUE_CERTIFICATE_$(printf '%s' "$environment" | tr '[:lower:]' '[:upper:]')"
[[ $confirm == "$expected" ]] || { echo "refusing apply: --confirm must equal $expected" >&2; exit 2; }
"${command[@]}"
echo "certificate request: PASS ($environment)"
