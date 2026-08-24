#!/usr/bin/env bash
set -euo pipefail
umask 077

environment=""
output_dir=""
confirm=""
apply=false
while (($#)); do
  case "$1" in
    --environment) environment=${2:?}; shift 2 ;;
    --output-dir) output_dir=${2:?}; shift 2 ;;
    --confirm) confirm=${2:?}; shift 2 ;;
    --apply) apply=true; shift ;;
    *) echo "usage: $0 --environment ENV --output-dir ABS [--apply --confirm GENERATE_JWT_ENV]" >&2; exit 2 ;;
  esac
done
[[ $environment =~ ^(dev|staging|production)$ && $output_dir == /* && ${output_dir##*/} == "$environment" && $output_dir != "/$environment" ]] || {
  echo "output-dir must be an absolute environment-scoped path ending /$environment" >&2; exit 2;
}
private="$output_dir/jwt-private.pem"
public="$output_dir/jwt-public.pem"
runtime_uid=10001
runtime_gid=10001
echo "JWT key plan: $private and $public (existing files are never overwritten)"
if ! $apply; then echo "DRY-RUN: no key generated"; exit 0; fi
expected="GENERATE_JWT_$(printf '%s' "$environment" | tr '[:lower:]' '[:upper:]')"
[[ $confirm == "$expected" ]] || { echo "refusing apply: --confirm must equal $expected" >&2; exit 2; }
[[ ${EUID:-$(id -u)} -eq 0 ]] || {
  echo "refusing apply: run as root so key ownership can be fixed to container UID/GID 10001" >&2
  exit 2
}
[[ ! -e $private && ! -e $public ]] || { echo "refusing to overwrite an existing JWT key" >&2; exit 1; }
mkdir -p "$output_dir"
temp_private=$(mktemp "$output_dir/.jwt-private.XXXXXX")
temp_public=$(mktemp "$output_dir/.jwt-public.XXXXXX")
trap 'rm -f "$temp_private" "$temp_public"' EXIT
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$temp_private"
openssl pkey -in "$temp_private" -pubout -out "$temp_public"
# Compose file secrets bind-mount the host file and do not remap ownership. The App
# runs as 10001:10001, so root must install both files with that numeric owner.
chown -- "$runtime_uid:$runtime_gid" "$temp_private" "$temp_public"
chmod 0400 "$temp_private"
chmod 0444 "$temp_public"
mv "$temp_private" "$private"
mv "$temp_public" "$public"
echo "JWT keypair generated; private key must never enter Git or backup logs"
