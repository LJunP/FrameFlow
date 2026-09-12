#!/usr/bin/env bash
set -euo pipefail

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
repo_root=$(CDPATH= cd -- "$script_dir/../.." && pwd -P)
temp_root=$(mktemp -d)
trap 'rm -rf "$temp_root"' EXIT

"$script_dir/validate-deploy-config.sh" >/dev/null

actual_env="$temp_root/dev.env"
python3 - "$repo_root/infra/dev/env.example" "$actual_env" <<'PY'
import pathlib, sys
text = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
replacements = {
    "ghcr.io/owner/repository/": "registry.frameflow.test/team/select/",
    "0" * 64: "a" * 64,
    "CHANGE_ME_DEV_DB_AT_LEAST_24_CHARS": "dev-db-7Jm9vQ2xT4pL8sR6wK3n",
    "CHANGE_ME_DEV_REDIS_AT_LEAST_24_CHARS": "dev-redis-4Ds8mN2qP7yV5tK9xL6c",
    "CHANGE_ME_DEV_RABBIT_AT_LEAST_24_CHARS": "dev-rabbit-9Kp3wT7mQ2vN8xL5sD4j",
    "CHANGE_ME_DEV_MINIO_AT_LEAST_24_CHARS": "dev-minio-6Vq2nL9xT4mK7pD3sW8r",
    "CHANGE_ME_DEV_WORKER_AT_LEAST_32_CHARS": "dev-worker-8Nx4qT2mV7pL9sK5wD3jR6c",
    "dev.frameflow.example.com": "dev.frameflow.test",
    "media.dev.frameflow.example.com": "media.dev.frameflow.test",
    "owner@example.com": "owner@frameflow.test",
}
for old, new in replacements.items():
    text = text.replace(old, new)
pathlib.Path(sys.argv[2]).write_text(text, encoding="utf-8")
PY
"$script_dir/check-env-isolation.sh" --environment dev --env-file "$actual_env" >/dev/null

# 证明校验器不会 source/执行 env value。
marker="$temp_root/should-not-exist"
printf 'UNUSED_VALUE=$(touch %s)\n' "$marker" >> "$actual_env"
"$script_dir/check-env-isolation.sh" --environment dev --env-file "$actual_env" >/dev/null
[[ ! -e $marker ]]

# 真实 Provider 的目录不含 Key；Key 只通过 0600 Worker env-file 注入。
provider_env="$temp_root/worker-provider.env"
provider_token="synthetic-provider-token-7Jm9vQ2xT4pL8sR6"
(umask 077; printf 'FRAMEFLOW_MODEL_TEST_RESPONSES_KEY=%s\n' "$provider_token" >"$provider_env")
python3 - "$actual_env" "$provider_env" <<'PY'
import json, pathlib, sys
path = pathlib.Path(sys.argv[1])
catalog = {
    "defaultModelId": "responses-test-v1",
    "models": [{
        "id": "responses-test-v1", "label": "Responses test",
        "description": "offline protocol fixture", "provider": "openai-responses",
        "model": "vision-test", "baseUrl": "https://provider.example/v1",
        "apiKeyEnv": "FRAMEFLOW_MODEL_TEST_RESPONSES_KEY", "enabled": True,
    }],
}
replacements = {
    "FRAMEFLOW_SEMANTIC_MODEL_CATALOG_JSON=":
        "FRAMEFLOW_SEMANTIC_MODEL_CATALOG_JSON=" + json.dumps(catalog, separators=(",", ":")),
    "FRAMEFLOW_WORKER_PROVIDER_ENV_FILE=/etc/frameflow/dev/worker-provider.env":
        "FRAMEFLOW_WORKER_PROVIDER_ENV_FILE=" + sys.argv[2],
}
text = path.read_text(encoding="utf-8")
for old, new in replacements.items():
    if old not in text:
        raise SystemExit(f"missing fixture line: {old}")
    text = text.replace(old, new, 1)
path.write_text(text, encoding="utf-8")
PY
"$script_dir/check-env-isolation.sh" --environment dev --env-file "$actual_env" >/dev/null

# Compose 展开后只有 Worker 能看到 token；App/Web 即使目录公开也拿不到 Secret。
docker compose --env-file "$actual_env" -f "$repo_root/infra/dev/docker-compose.yml" \
  config --format json | python3 -c '
import json, sys
token = sys.argv[1]
services = json.load(sys.stdin)["services"]
worker_values = services["worker"].get("environment", {}).values()
if token not in worker_values:
    raise SystemExit("Worker Provider token was not injected")
for service in ("app", "web"):
    if token in services[service].get("environment", {}).values():
        raise SystemExit(f"Provider token leaked into {service}")
' "$provider_token"

chmod 0644 "$provider_env"
if "$script_dir/check-env-isolation.sh" --environment dev --env-file "$actual_env" >/dev/null 2>&1; then
  echo "expected permissive Provider env-file mode to fail" >&2
  exit 1
fi
chmod 0600 "$provider_env"

bad_provider_env="$temp_root/bad-worker-provider.env"
(umask 077; printf 'UNDECLARED_PROVIDER_KEY=%s\n' "$provider_token" >"$bad_provider_env")
bad_provider_config="$temp_root/bad-provider-config.env"
sed "s|FRAMEFLOW_WORKER_PROVIDER_ENV_FILE=$provider_env|FRAMEFLOW_WORKER_PROVIDER_ENV_FILE=$bad_provider_env|" \
  "$actual_env" >"$bad_provider_config"
if "$script_dir/check-env-isolation.sh" --environment dev --env-file "$bad_provider_config" >/dev/null 2>&1; then
  echo "expected undeclared Worker Provider key to fail" >&2
  exit 1
fi

captured_provider_env="$temp_root/captured-worker-provider.env"
printf 'synthetic-luna-token-4Ds8mN2qP7yV5tK9\n' |
  "$script_dir/capture-provider-secrets.sh" \
    --catalog "$repo_root/experiments/fixtures/pre-f9-correctness/model-catalog.opencode-go-luna.example.json" \
    --output "$captured_provider_env" >/dev/null 2>/dev/null
python3 - "$captured_provider_env" <<'PY'
import pathlib, stat, sys
path = pathlib.Path(sys.argv[1])
if stat.S_IMODE(path.stat().st_mode) != 0o600:
    raise SystemExit("captured Provider env-file is not 0600")
line = path.read_text(encoding="utf-8").splitlines()
if len(line) != 1 or not line[0].startswith("FRAMEFLOW_MODEL_OPENCODE_GO_LUNA_V1_API_KEY="):
    raise SystemExit("captured Provider env-file has an unexpected key set")
PY
if printf 'another-synthetic-token-4Ds8mN2qP7y\n' |
  "$script_dir/capture-provider-secrets.sh" \
    --catalog "$repo_root/experiments/fixtures/pre-f9-correctness/model-catalog.opencode-go-luna.example.json" \
    --output "$captured_provider_env" >/dev/null 2>&1; then
  echo "expected Provider secret overwrite without confirmation to fail" >&2
  exit 1
fi

bad_keys="$temp_root/bad-keys.env"
cp "$actual_env" "$bad_keys"
python3 - "$bad_keys" <<'PY'
import pathlib, sys
p = pathlib.Path(sys.argv[1])
text = p.read_text(encoding="utf-8")
private = next(line.split("=", 1)[1] for line in text.splitlines() if line.startswith("FRAMEFLOW_JWT_PRIVATE_KEY_HOST_FILE="))
text = "\n".join(
    "FRAMEFLOW_JWT_PUBLIC_KEY_HOST_FILE=" + private if line.startswith("FRAMEFLOW_JWT_PUBLIC_KEY_HOST_FILE=") else line
    for line in text.splitlines()
) + "\n"
p.write_text(text, encoding="utf-8")
PY
if "$script_dir/check-env-isolation.sh" --environment dev --env-file "$bad_keys" >/dev/null 2>&1; then
  echo "expected same-path JWT key validation failure" >&2
  exit 1
fi

manifest="$temp_root/image-manifest.json"
SOURCE_DATE_EPOCH=1767225600 "$script_dir/generate-image-manifest.sh" \
  --version 0.1.0 --git-sha 0123456789abcdef0123456789abcdef01234567 --source-run offline-test \
  --app-image "registry.frameflow.test/team/app:git-0123456789ab@sha256:$(printf '1%.0s' {1..64})" \
  --worker-image "registry.frameflow.test/team/worker:git-0123456789ab@sha256:$(printf '2%.0s' {1..64})" \
  --web-image "registry.frameflow.test/team/web:git-0123456789ab@sha256:$(printf '3%.0s' {1..64})" \
  --output "$manifest" >/dev/null
"$script_dir/validate-image-manifest.sh" "$manifest" --expected-git-sha 0123456789abcdef0123456789abcdef01234567 >/dev/null
release_env="$temp_root/release.env"
"$script_dir/validate-image-manifest.sh" "$manifest" --emit-env > "$release_env"
grep -q '^FRAMEFLOW_APP_IMAGE=.*@sha256:' "$release_env"

bad_manifest="$temp_root/bad-manifest.json"
python3 - "$manifest" "$bad_manifest" <<'PY'
import json, pathlib, sys
raw = json.loads(pathlib.Path(sys.argv[1]).read_text())
raw["images"][0]["tag"] = "latest"
pathlib.Path(sys.argv[2]).write_text(json.dumps(raw), encoding="utf-8")
PY
if "$script_dir/validate-image-manifest.sh" "$bad_manifest" >/dev/null 2>&1; then
  echo "expected manifest mismatch failure" >&2
  exit 1
fi

# ops bundle 安装器只能从 manifest 指向的真实 Git object 取 tracked infra bytes；
# 本地门禁只执行 dry-run，不写 /opt。
head_sha="$(git -C "$repo_root" rev-parse HEAD)"
ops_manifest="$temp_root/ops-image-manifest.json"
SOURCE_DATE_EPOCH=1767225600 "$script_dir/generate-image-manifest.sh" \
  --version 0.1.0 --git-sha "$head_sha" --source-run offline-ops-test \
  --app-image "registry.frameflow.test/team/app:git-${head_sha:0:12}@sha256:$(printf '4%.0s' {1..64})" \
  --worker-image "registry.frameflow.test/team/worker:git-${head_sha:0:12}@sha256:$(printf '5%.0s' {1..64})" \
  --web-image "registry.frameflow.test/team/web:git-${head_sha:0:12}@sha256:$(printf '6%.0s' {1..64})" \
  --output "$ops_manifest" >/dev/null
"$script_dir/install-ops-bundle.sh" --source-root "$repo_root" --manifest "$ops_manifest" |
  grep -q 'DRY-RUN: no /opt files or current pointer changed'

# 正式 handoff 不携带 .git；它必须能用 manifest SHA + 完整 file-set checksum 的
# prebuilt bundle 走同一安装器，并在任一 byte 被改写时 fail-closed。
ops_bundle="$temp_root/ops-bundle"
mkdir -p "$ops_bundle"
# 本门禁运行在尚未提交的交付工作树上，因此用当前待交付 bytes 建立 fixture；正式
# Workflow 仍只能从已验证 manifest 的精确 commit 执行 git archive。
cp -R "$repo_root/infra" "$ops_bundle/"
printf '%s\n' "$head_sha" >"$ops_bundle/OPS_GIT_SHA"
(
  cd "$ops_bundle"
  find infra -type f -print0 | LC_ALL=C sort -z | xargs -0 sha256sum >OPS_SHA256SUMS
)
"$script_dir/install-ops-bundle.sh" --bundle-root "$ops_bundle" --manifest "$ops_manifest" |
  grep -q 'DRY-RUN: no /opt files or current pointer changed'
printf '\nTAMPERED\n' >>"$ops_bundle/infra/README.md"
if "$script_dir/install-ops-bundle.sh" --bundle-root "$ops_bundle" --manifest "$ops_manifest" \
  >/dev/null 2>&1; then
  echo "expected tampered handoff ops bundle to fail" >&2
  exit 1
fi

state_dir="$temp_root/frameflow/dev"
"$script_dir/promote-release.sh" --environment dev \
  --compose-file "$repo_root/infra/dev/docker-compose.yml" --env-file "$actual_env" \
  --manifest "$manifest" --state-dir "$state_dir" --base-url https://dev.frameflow.test >/dev/null

# rollback 在任何 Docker 操作前都必须证明 release.env 是已验证 manifest 的精确投影。
release_sha=0123456789abcdef0123456789abcdef01234567
target_dir="$state_dir/releases/$release_sha"
mkdir -p "$target_dir"
cp "$manifest" "$target_dir/image-manifest.json"
cp "$release_env" "$target_dir/release.env"
"$script_dir/rollback.sh" --environment dev \
  --compose-file "$repo_root/infra/dev/docker-compose.yml" --env-file "$actual_env" \
  --state-dir "$state_dir" --base-url https://dev.frameflow.test --target "$release_sha" >/dev/null
printf 'FRAMEFLOW_APP_IMAGE=registry.frameflow.test/tampered:tag@sha256:%064d\n' 0 >>"$target_dir/release.env"
if "$script_dir/rollback.sh" --environment dev \
  --compose-file "$repo_root/infra/dev/docker-compose.yml" --env-file "$actual_env" \
  --state-dir "$state_dir" --base-url https://dev.frameflow.test --target "$release_sha" \
  >/dev/null 2>&1; then
  echo "expected tampered release.env to fail before rollback" >&2
  exit 1
fi
"$script_dir/render-nginx-config.sh" --environment dev --env-file "$actual_env" --mode https \
  | grep -q 'server_name dev.frameflow.test'
"$script_dir/request-certificate.sh" --environment dev --env-file "$actual_env" >/dev/null
"$script_dir/bootstrap-vps.sh" --host-class nonprod >/dev/null
"$script_dir/generate-jwt-keypair.sh" --environment dev --output-dir "$temp_root/keys/dev" >/dev/null
"$script_dir/renew-certificates.sh" >/dev/null

# 自定义 smoke project 仅用于隔离本地测试；不允许任意名字触达其他项目。
if "$script_dir/smoke-test.sh" --environment local \
  --compose-file "$repo_root/infra/local/docker-compose.yml" \
  --env-file "$repo_root/infra/local/env.example" --base-url http://127.0.0.1:3000 \
  --project-name unrelated-project --timeout 0 >"$temp_root/smoke-rejected.log" 2>&1; then
  echo "expected invalid smoke project name to fail" >&2; exit 1
fi
grep -q 'custom project name is restricted' "$temp_root/smoke-rejected.log"

echo "F9 script tests: PASS"
