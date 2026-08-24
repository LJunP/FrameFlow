#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
MONITORING_ROOT="$(cd "$SCRIPT_DIR/.." && pwd -P)"
[[ -f "$MONITORING_ROOT/.f10-monitoring-root" ]] || { echo "ERROR: monitoring root sentinel missing" >&2; exit 2; }

usage() {
  cat >&2 <<'EOF'
Usage: provision-exporter-credentials.sh --environment <env> --compose-file <absolute>
  --env-file <absolute> --project-name <frameflow-*> --database <name>
  --postgres-admin <name> --rabbit-vhost </name> --output-env-file <absolute new path>
  [--confirmation PROVISION-MONITORING:<env>:<project>] [--allow-production] [--execute]

On --execute the operator must export two new secrets (they are never printed):
  FRAMEFLOW_MONITOR_POSTGRES_PASSWORD
  FRAMEFLOW_MONITOR_REDIS_PASSWORD
EOF
}

environment="" compose_file="" env_file="" project="" database="" postgres_admin=""
rabbit_vhost="" output="" confirmation="" allow_production=false execute=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --environment) environment="${2:-}"; shift 2 ;;
    --compose-file) compose_file="${2:-}"; shift 2 ;;
    --env-file) env_file="${2:-}"; shift 2 ;;
    --project-name) project="${2:-}"; shift 2 ;;
    --database) database="${2:-}"; shift 2 ;;
    --postgres-admin) postgres_admin="${2:-}"; shift 2 ;;
    --rabbit-vhost) rabbit_vhost="${2:-}"; shift 2 ;;
    --output-env-file) output="${2:-}"; shift 2 ;;
    --confirmation) confirmation="${2:-}"; shift 2 ;;
    --allow-production) allow_production=true; shift ;;
    --execute) execute=true; shift ;;
    *) usage; exit 2 ;;
  esac
done

[[ "$environment" =~ ^(local|dev|staging|production)$ ]] || { echo "ERROR: invalid environment" >&2; exit 2; }
[[ "$compose_file" = /* && -f "$compose_file" && ! -L "$compose_file" ]] || { echo "ERROR: invalid compose file" >&2; exit 2; }
[[ "$env_file" = /* && -f "$env_file" && ! -L "$env_file" ]] || { echo "ERROR: invalid env file" >&2; exit 2; }
[[ "$project" =~ ^frameflow-[a-z0-9-]+$ && "$project" == *"$environment"* ]] || { echo "ERROR: project/environment mismatch" >&2; exit 2; }
[[ "$database" =~ ^[a-zA-Z0-9_.-]+$ && "$postgres_admin" =~ ^[a-zA-Z0-9_.-]+$ ]] || { echo "ERROR: unsafe database/admin identifier" >&2; exit 2; }
[[ "$rabbit_vhost" =~ ^/[a-zA-Z0-9_.-]+$ ]] || { echo "ERROR: rabbit vhost must be an explicit /name" >&2; exit 2; }
[[ "$output" = /* && ! -e "$output" ]] || { echo "ERROR: output must be an absolute new path" >&2; exit 2; }
if [[ "$environment" == production ]]; then
  expected="PROVISION-MONITORING:$environment:$project"
  [[ "$allow_production" == true && "$confirmation" == "$expected" ]] || { echo "ERROR: production gate requires --allow-production and $expected" >&2; exit 2; }
fi

if [[ "$execute" != true ]]; then
  echo "DRY-RUN: would create/reconcile dedicated pg_monitor and Redis ACL users, then verify the RabbitMQ vhost; no secret is read."
  exit 0
fi

: "${FRAMEFLOW_MONITOR_POSTGRES_PASSWORD:?required}"
: "${FRAMEFLOW_MONITOR_REDIS_PASSWORD:?required}"
for secret in "$FRAMEFLOW_MONITOR_POSTGRES_PASSWORD" "$FRAMEFLOW_MONITOR_REDIS_PASSWORD"; do
  [[ "$secret" =~ ^[a-zA-Z0-9._+@%-]{20,128}$ ]] || { echo "ERROR: monitor secrets must be 20..128 safe dotenv characters" >&2; exit 2; }
done
command -v docker >/dev/null 2>&1 || { echo "ERROR: Docker is required" >&2; exit 2; }
compose=(docker compose --env-file "$env_file" -p "$project" -f "$compose_file")
"${compose[@]}" config --quiet

# PostgreSQL: dedicated login receives pg_monitor only, never ownership/write grants.
FRAMEFLOW_MONITOR_PASSWORD="$FRAMEFLOW_MONITOR_POSTGRES_PASSWORD" python3 - <<'PY' |
import os
password = os.environ["FRAMEFLOW_MONITOR_PASSWORD"].replace("'", "''")
print("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='frameflow_monitor') THEN CREATE ROLE frameflow_monitor LOGIN; END IF; END $$;")
print(f"ALTER ROLE frameflow_monitor WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS INHERIT PASSWORD '{password}';")
print("DO $$ DECLARE inherited_role record; BEGIN FOR inherited_role IN SELECT parent.rolname FROM pg_auth_members m JOIN pg_roles parent ON parent.oid = m.roleid JOIN pg_roles member_role ON member_role.oid = m.member WHERE member_role.rolname = 'frameflow_monitor' AND parent.rolname <> 'pg_monitor' LOOP EXECUTE format('REVOKE %I FROM frameflow_monitor', inherited_role.rolname); END LOOP; END $$;")
print("GRANT pg_monitor TO frameflow_monitor;")
print("REVOKE ADMIN OPTION FOR pg_monitor FROM frameflow_monitor;")
PY
  "${compose[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 --username "$postgres_admin" --dbname "$database"

"${compose[@]}" exec -T -e FRAMEFLOW_TARGET_VHOST="$rabbit_vhost" rabbitmq sh -ec '
  rabbitmqctl list_vhosts -q | grep -Fx "$FRAMEFLOW_TARGET_VHOST"
  rabbitmq-plugins list -e -m | grep -Fx rabbitmq_prometheus
'

"${compose[@]}" exec -T -e FRAMEFLOW_MONITOR_PASSWORD="$FRAMEFLOW_MONITOR_REDIS_PASSWORD" redis sh -ec '
  redis-cli --no-auth-warning -a "$FRAMEFLOW_REDIS_PASSWORD" ACL SETUSER frameflow_monitor \
    reset on ">${FRAMEFLOW_MONITOR_PASSWORD}" -@all \
    +ping +info "+client|setname" "+latency|latest" "+latency|histogram" \
    "+slowlog|len" "+slowlog|get" resetkeys resetchannels >/dev/null
  info_dryrun="$(redis-cli --no-auth-warning -a "$FRAMEFLOW_REDIS_PASSWORD" --raw ACL DRYRUN frameflow_monitor INFO)"
  [ "$info_dryrun" = OK ] || {
    echo "ERROR: Redis monitor ACL does not allow INFO" >&2
    exit 2
  }
  get_dryrun="$(redis-cli --no-auth-warning -a "$FRAMEFLOW_REDIS_PASSWORD" --raw ACL DRYRUN frameflow_monitor GET frameflow:acl-probe 2>&1 || true)"
  [ "$get_dryrun" != OK ] || {
    echo "ERROR: Redis monitor ACL unexpectedly allows GET" >&2
    exit 2
  }
'

umask 077
mkdir -p "$(dirname "$output")"
FRAMEFLOW_OUTPUT="$output" FRAMEFLOW_DATABASE="$database" \
FRAMEFLOW_PG_PASSWORD="$FRAMEFLOW_MONITOR_POSTGRES_PASSWORD" \
FRAMEFLOW_REDIS_PASSWORD_OUT="$FRAMEFLOW_MONITOR_REDIS_PASSWORD" python3 - <<'PY'
import os
from pathlib import Path
from urllib.parse import quote

pg_password = quote(os.environ["FRAMEFLOW_PG_PASSWORD"], safe="")
database = quote(os.environ["FRAMEFLOW_DATABASE"], safe="")
lines = [
    f"FRAMEFLOW_POSTGRES_EXPORTER_DSN=postgresql://frameflow_monitor:{pg_password}@postgres:5432/{database}?sslmode=disable",
    "FRAMEFLOW_REDIS_EXPORTER_USER=frameflow_monitor",
    f"FRAMEFLOW_REDIS_EXPORTER_PASSWORD={os.environ['FRAMEFLOW_REDIS_PASSWORD_OUT']}",
]
if any("\n" in line or "\r" in line for line in lines):
    raise SystemExit("secret contains newline")
Path(os.environ["FRAMEFLOW_OUTPUT"]).write_text("\n".join(lines) + "\n", encoding="utf-8")
PY
chmod 600 "$output"
echo "Exporter credentials provisioned and env written to $output (secrets not printed)."
