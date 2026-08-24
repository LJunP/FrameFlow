#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
MONITORING_ROOT="$(cd "$SCRIPT_DIR/.." && pwd -P)"
REPO_ROOT="$(cd "$MONITORING_ROOT/../.." && pwd -P)"
[[ -f "$MONITORING_ROOT/.f10-monitoring-root" ]] || {
  echo "ERROR: monitoring root sentinel missing" >&2
  exit 2
}

mode="static"
if [[ $# -gt 0 ]]; then
  [[ "$1" == "--docker" && $# -eq 1 ]] || { echo "Usage: $0 [--docker]" >&2; exit 2; }
  mode="docker"
fi

required=(
  docker-compose.observability.yml
  prometheus/prometheus.yml
  prometheus/rules/frameflow-alerts.yml
  alertmanager/alertmanager.local.yml
  loki/loki.yml
  alloy/config.alloy
  grafana/provisioning/datasources/datasources.yml
  grafana/provisioning/dashboards/dashboards.yml
  grafana/dashboards/frameflow-operations.json
)
for relative in "${required[@]}"; do
  [[ -s "$MONITORING_ROOT/$relative" ]] || { echo "ERROR: missing $relative" >&2; exit 1; }
done

python3 -m json.tool "$MONITORING_ROOT/grafana/dashboards/frameflow-operations.json" >/dev/null
FRAMEFLOW_ALERT_SINK="$MONITORING_ROOT/scripts/alert_sink.py" python3 - <<'PY'
import os
from pathlib import Path

source = Path(os.environ["FRAMEFLOW_ALERT_SINK"]).read_text(encoding="utf-8")
compile(source, os.environ["FRAMEFLOW_ALERT_SINK"], "exec")
PY
for script in "$MONITORING_ROOT"/scripts/*.sh; do
  bash -n "$script"
done
grep -q 'profiles: \[synthetic-alert-drill\]' "$MONITORING_ROOT/docker-compose.observability.yml" || {
  echo "ERROR: local alert sink must remain behind synthetic-alert-drill profile" >&2
  exit 1
}

compose_file="$MONITORING_ROOT/docker-compose.observability.yml"
if grep -Eqi 'grafana/promtail|^[[:space:]]{2}promtail:|kbudde/rabbitmq-exporter' \
  "$MONITORING_ROOT"/docker-compose*.yml; then
  echo "ERROR: EOL Promtail or archived kbudde exporter is forbidden" >&2
  exit 1
fi

FRAMEFLOW_OBSERVABILITY_COMPOSE="$compose_file" python3 - <<'PY'
import os
from pathlib import Path

lines = Path(os.environ["FRAMEFLOW_OBSERVABILITY_COMPOSE"]).read_text(encoding="utf-8").splitlines()
service_start = lines.index("services:") + 1
services = {}
current = None
for line in lines[service_start:]:
    if line and not line.startswith(" "):
        break
    if line.startswith("  ") and not line.startswith("    ") and line.endswith(":"):
        current = line[2:-1]
        services[current] = []
    elif current is not None:
        services[current].append(line)
alloy = "\n".join(services.get("alloy", []))
if "docker-socket-proxy" in services:
    raise SystemExit("ERROR: monitoring must not retain a Docker API proxy")
if "networks: [backend]" not in alloy:
    raise SystemExit("ERROR: Alloy must join only the backend application network")
if "FRAMEFLOW_ALLOY_SYSLOG_PORT" not in alloy or ":1514/udp" not in alloy:
    raise SystemExit("ERROR: Alloy must expose the RFC5424 listener on host loopback UDP")
if "cap_drop: [ALL]" not in alloy:
    raise SystemExit("ERROR: Alloy must drop all Linux capabilities")
if 'user: "473:473"' not in alloy:
    raise SystemExit("ERROR: Alloy must run as its image-owned non-root UID/GID 473")
PY

if rg -n '/var/run/docker\.sock|docker-socket-proxy|discovery\.docker|loki\.source\.docker|CONTAINERS:[[:space:]]*"?1' \
  "$MONITORING_ROOT"/docker-compose*.yml "$MONITORING_ROOT/alloy"; then
  echo "ERROR: monitoring must have no Docker socket, API proxy, discovery, or logs API path" >&2
  exit 1
fi
grep -Fq 'loki.source.syslog "frameflow"' "$MONITORING_ROOT/alloy/config.alloy" &&
  grep -Fq 'protocol               = "udp"' "$MONITORING_ROOT/alloy/config.alloy" &&
  grep -Fq 'regex         = "frameflow-(app|worker)"' "$MONITORING_ROOT/alloy/config.alloy" || {
  echo "ERROR: Alloy must accept only App/Worker RFC5424 syslog" >&2
  exit 1
}

for base_compose in "$REPO_ROOT/infra/local/docker-compose.yml" "$REPO_ROOT/infra/scripts/remote-compose.yml"; do
  FRAMEFLOW_BASE_COMPOSE="$base_compose" python3 - <<'PY'
import os
from pathlib import Path

lines = Path(os.environ["FRAMEFLOW_BASE_COMPOSE"]).read_text(encoding="utf-8").splitlines()
service_start = lines.index("services:") + 1
services = {}
current = None
for line in lines[service_start:]:
    if line and not line.startswith(" "):
        break
    if line.startswith("  ") and not line.startswith("    ") and line.endswith(":"):
        current = line[2:-1]
        services[current] = []
    elif current is not None:
        services[current].append(line)
for service in ("app", "worker"):
    block = "\n".join(services.get(service, []))
    required = (
        "driver: syslog", "syslog-address:", "syslog-format: rfc5424micro",
        f"tag: frameflow-{service}", "mode: non-blocking", 'cache-disabled: "false"',
    )
    if any(item not in block for item in required):
        raise SystemExit(f"ERROR: {service} safe syslog/dual-cache contract missing")
PY
done

for image in \
  'prom/prometheus:v3.13.1' \
  'prom/alertmanager:v0.32.1' \
  'grafana/grafana:13.1.0' \
  'grafana/loki:3.7.4' \
  'grafana/alloy:v1.18.0' \
  'prometheuscommunity/postgres-exporter:v0.20.1' \
  'oliver006/redis_exporter:v1.89.0-alpine'; do
  grep -Fq "image: $image" "$compose_file" || {
    echo "ERROR: required monitoring image missing: $image" >&2
    exit 1
  }
done

plugin_file="$REPO_ROOT/infra/rabbitmq/enabled_plugins"
grep -Fqx '[rabbitmq_management,rabbitmq_prometheus].' "$plugin_file" || {
  echo "ERROR: RabbitMQ official Prometheus plugin is not enabled" >&2
  exit 1
}
for base_compose in "$REPO_ROOT/infra/local/docker-compose.yml" "$REPO_ROOT/infra/scripts/remote-compose.yml"; do
  grep -Fq '../rabbitmq/enabled_plugins:/etc/rabbitmq/enabled_plugins:ro' "$base_compose" &&
    grep -Fq 'expose: ["15692"]' "$base_compose" || {
      echo "ERROR: RabbitMQ plugin mount/15692 expose missing in $base_compose" >&2
      exit 1
    }
done

grep -Fq 'targets: ["rabbitmq:15692"]' "$MONITORING_ROOT/prometheus/prometheus.yml" &&
  grep -Fq 'metrics_path: /metrics/detailed' "$MONITORING_ROOT/prometheus/prometheus.yml" &&
  grep -Fq 'queue_coarse_metrics' "$MONITORING_ROOT/prometheus/prometheus.yml" &&
  grep -Fq 'queue_consumer_count' "$MONITORING_ROOT/prometheus/prometheus.yml" || {
  echo "ERROR: RabbitMQ official basic/detailed scrapes are incomplete" >&2
  exit 1
}

credentials_script="$MONITORING_ROOT/scripts/provision-exporter-credentials.sh"
grep -Fq 'reset on ">${FRAMEFLOW_MONITOR_PASSWORD}" -@all' "$credentials_script" &&
  grep -Fq 'ACL DRYRUN frameflow_monitor INFO' "$credentials_script" &&
  grep -Fq 'ACL DRYRUN frameflow_monitor GET frameflow:acl-probe' "$credentials_script" &&
  grep -Fq 'REDIS_EXPORTER_CONFIG_COMMAND: "-"' "$compose_file" || {
  echo "ERROR: Redis exporter ACL/config boundary is incomplete" >&2
  exit 1
}
if grep -Fq '"~*"' "$credentials_script" || grep -Eq '(^|[[:space:]])\+get([[:space:]]|$)' "$credentials_script"; then
  echo "ERROR: Redis exporter ACL must not receive key access" >&2
  exit 1
fi
if grep -q 'FRAMEFLOW_RABBITMQ_EXPORTER_' "$credentials_script"; then
  echo "ERROR: RabbitMQ exporter credentials must not be provisioned" >&2
  exit 1
fi
grep -Fq 'LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS INHERIT' "$credentials_script" &&
  grep -Fq "parent.rolname <> 'pg_monitor'" "$credentials_script" &&
  grep -Fq 'REVOKE ADMIN OPTION FOR pg_monitor FROM frameflow_monitor' "$credentials_script" || {
  echo "ERROR: PostgreSQL monitor role hardening/membership reconciliation is incomplete" >&2
  exit 1
}
grep -Fq 'pg_up or redis_up or up{job=\"rabbitmq\"}' "$MONITORING_ROOT/grafana/dashboards/frameflow-operations.json" || {
  echo "ERROR: dependency dashboard must include RabbitMQ official scrape health" >&2
  exit 1
}

alerts=(FrameFlowAppDown FrameFlowWorkerDown FrameFlowPostgresDown FrameFlowRedisDown \
  FrameFlowRabbitMQDown \
  FrameFlowHighHttp5xxRatio FrameFlowHighHttpP95Latency FrameFlowAnalysisQueueBacklog \
  FrameFlowWorkerFailuresBurst FrameFlowRedisDegradationBurst)
for alert in "${alerts[@]}"; do
  grep -q "alert: $alert" "$MONITORING_ROOT/prometheus/rules/frameflow-alerts.yml" || {
    echo "ERROR: alert rule missing: $alert" >&2
    exit 1
  }
done

grep -Fq 'or frameflow_worker_ready != 1' "$MONITORING_ROOT/prometheus/rules/frameflow-alerts.yml" &&
  grep -Fq 'or absent(frameflow_worker_ready)' "$MONITORING_ROOT/prometheus/rules/frameflow-alerts.yml" &&
  grep -Fq 'frameflow_worker_ready' "$MONITORING_ROOT/grafana/dashboards/frameflow-operations.json" || {
    echo "ERROR: Worker availability must cover scrape, missing readiness, and readiness=0" >&2
    exit 1
  }

grep -q 'provision-exporter-credentials.sh' "$MONITORING_ROOT/README.md" &&
  grep -q 'observability-stack.sh' "$MONITORING_ROOT/README.md" || {
  echo "ERROR: runbook must provision dedicated PG/Redis exporter users before controlled startup" >&2
  exit 1
}

render_alertmanager="$MONITORING_ROOT/scripts/render-alertmanager-config.sh"
observability_wrapper="$MONITORING_ROOT/scripts/observability-stack.sh"
grep -Fq '[[ "$EUID" -eq 0 ]]' "$render_alertmanager" &&
  grep -Fq 'os.O_EXCL' "$render_alertmanager" &&
  grep -Fq 'os.O_NOFOLLOW' "$render_alertmanager" &&
  grep -Fq 'chmod 0400 "$output"' "$render_alertmanager" &&
  grep -Fq 'chown 65534:65534 "$output"' "$render_alertmanager" &&
  grep -Fq 'runtime_owner="$(numeric_owner "$output")"' "$render_alertmanager" &&
  grep -Fq 'Linux) stat -c '\''%u:%g'\'' "$1"' "$render_alertmanager" &&
  grep -Fq '[[ "$runtime_permissions" == "400" && "$runtime_owner" == "65534:65534" ]]' \
    "$render_alertmanager" || {
  echo "ERROR: external Alertmanager renderer must apply and verify root-only 65534:65534/0400 ownership" >&2
  exit 1
}
grep -Fq 'owner="$(numeric_owner "$alert_config")"' "$observability_wrapper" &&
  grep -Fq 'Linux) stat -c '\''%a'\'' "$1"' "$observability_wrapper" &&
  grep -Fq '[[ "$permissions" == "400" && "$owner" == "65534:65534" ]]' \
    "$observability_wrapper" || {
  echo "ERROR: observability wrapper must reject external configs outside 65534:65534/0400" >&2
  exit 1
}

if [[ "$mode" == "docker" ]]; then
  command -v docker >/dev/null 2>&1 || { echo "ERROR: Docker is required for --docker" >&2; exit 2; }
  docker run --rm --entrypoint promtool \
    -v "$MONITORING_ROOT/prometheus:/etc/prometheus:ro" \
    prom/prometheus:v3.13.1 \
    check config /etc/prometheus/prometheus.yml
  docker run --rm --entrypoint amtool \
    -v "$MONITORING_ROOT/alertmanager/alertmanager.local.yml:/etc/alertmanager/alertmanager.yml:ro" \
    prom/alertmanager:v0.32.1 \
    check-config /etc/alertmanager/alertmanager.yml
  docker run --rm \
    -v "$MONITORING_ROOT/loki/loki.yml:/etc/loki/loki.yml:ro" \
    grafana/loki:3.7.4 \
    -config.file=/etc/loki/loki.yml -verify-config=true
  docker run --rm \
    -e FRAMEFLOW_ENV=local \
    -v "$MONITORING_ROOT/alloy/config.alloy:/etc/alloy/config.alloy:ro" \
    grafana/alloy:v1.18.0 \
    validate /etc/alloy/config.alloy
fi

echo "F10 monitoring validation: PASS ($mode)"
