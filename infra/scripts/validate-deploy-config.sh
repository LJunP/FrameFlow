#!/usr/bin/env bash
set -euo pipefail

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
repo_root=$(CDPATH= cd -- "$script_dir/../.." && pwd -P)
cd "$repo_root"

"$script_dir/check-env-isolation.sh" --environment local --env-file infra/local/env.example
"$script_dir/check-env-isolation.sh" --template --matrix \
  infra/dev/env.example infra/staging/env.example infra/production/env.example
"$script_dir/validate-image-manifest.sh" infra/scripts/image-manifest.example.json

if rg -n --glob 'Dockerfile' --glob '*.yml' --glob '*.yaml' '(^|[[:space:]])image:[[:space:]]*[^#[:space:]]*:latest([[:space:]]|$)' \
  frameflow-app frameflow-ai-worker frameflow-web infra .github; then
  echo "deploy config: FAIL: mutable latest image found" >&2
  exit 1
fi
if rg -n --glob '*.yml' --glob '*.yaml' 'container_name:' infra; then
  echo "deploy config: FAIL: container_name defeats Compose project isolation" >&2
  exit 1
fi

docker compose --env-file infra/local/env.example -f infra/local/docker-compose.yml config --quiet
for environment in dev staging production; do
  docker compose --env-file "infra/$environment/env.example" \
    -f "infra/$environment/docker-compose.yml" config --quiet
  docker compose --env-file "infra/$environment/env.example" \
    -f "infra/$environment/docker-compose.yml" config --format json |
    python3 -c '
import json
import sys

environment = sys.argv[1]
model = json.load(sys.stdin)
services = model["services"]
networks = model["networks"]

minio_networks = set(services["minio"].get("networks", {}))
if minio_networks != {"data", "media"}:
    raise SystemExit(f"{environment}: MinIO must join exactly data+media networks")
if not networks.get("data", {}).get("internal", False):
    raise SystemExit(f"{environment}: data network must remain internal")
if networks.get("media", {}).get("internal", False):
    raise SystemExit(f"{environment}: media network must allow loopback port publishing")
if services["app"].get("user") != "10001:10001":
    raise SystemExit(f"{environment}: App runtime UID/GID must remain 10001:10001")

ports = services["minio"].get("ports", [])
if len(ports) != 1 or ports[0].get("host_ip") != "127.0.0.1" or ports[0].get("target") != 9000:
    raise SystemExit(f"{environment}: MinIO API must publish only 127.0.0.1 -> 9000")
' "$environment"
done

grep -Fq 'runtime_uid=10001' infra/scripts/generate-jwt-keypair.sh &&
  grep -Fq 'runtime_gid=10001' infra/scripts/generate-jwt-keypair.sh &&
  grep -Fq 'chown -- "$runtime_uid:$runtime_gid"' infra/scripts/generate-jwt-keypair.sh || {
    echo "deploy config: FAIL: JWT key ownership no longer matches App runtime user" >&2
    exit 1
  }

for release_script in infra/scripts/promote-release.sh infra/scripts/rollback.sh; do
  grep -Fq -- '--emit-env' "$release_script" &&
    grep -Fq 'cmp -s' "$release_script" || {
      echo "deploy config: FAIL: release.env is not bound to the validated manifest: $release_script" >&2
      exit 1
    }
done

handoff=.github/workflows/cd-promotion-handoff.yml
for binding in \
  '"head_branch": "frameflow-select/learning-main"' \
  'release.get("gitSha") != git_sha' \
  'ref: ${{ steps.source.outputs.git_sha }}' \
  '--expected-git-sha "$MANIFEST_SHA"' \
  'git archive --format=tar "$MANIFEST_SHA" infra' \
  'OPS_SHA256SUMS'; do
  grep -Fq -- "$binding" "$handoff" || {
    echo "deploy config: FAIL: handoff is not bound to the source run manifest: $binding" >&2
    exit 1
  }
done

echo "deploy config: PASS"
