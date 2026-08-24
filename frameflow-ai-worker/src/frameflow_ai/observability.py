"""F10 worker JSON logging and finite-cardinality Prometheus metrics."""

from __future__ import annotations

import json
import logging
import sys
from datetime import datetime, timezone
from time import perf_counter
from typing import Final

from prometheus_client import CollectorRegistry, Counter, Gauge, Histogram, REGISTRY
from prometheus_client import start_http_server


_EXTRA_FIELDS: Final = (
    "event", "run_id", "delivery_attempt", "outcome", "queue", "worker_version"
)
_OUTCOMES: Final = (
    "succeeded", "analysis_error", "report_rejected", "requeue", "invalid_message"
)


class JsonFormatter(logging.Formatter):
    """Single-line JSON; secrets/config objects are never copied into structured fields."""

    def __init__(self, environment: str):
        super().__init__()
        self._environment = environment

    def format(self, record: logging.LogRecord) -> str:
        document: dict[str, object] = {
            "@timestamp": datetime.now(timezone.utc).isoformat(),
            "level": record.levelname,
            "service": "frameflow-worker",
            "environment": self._environment,
            "logger_name": record.name,
            "message": record.getMessage(),
        }
        for field in _EXTRA_FIELDS:
            value = getattr(record, field, None)
            if value is not None:
                document[field] = value
        if record.exc_info:
            document["exception"] = self.formatException(record.exc_info)
        return json.dumps(document, ensure_ascii=False, separators=(",", ":"))


def configure_json_logging(environment: str, level: str = "INFO") -> None:
    """Replace root handlers once at process entry so every module emits the same schema."""
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonFormatter(environment))
    root = logging.getLogger()
    root.handlers.clear()
    root.addHandler(handler)
    root.setLevel(level.upper())


class WorkerMetrics:
    """Metrics labels are pre-created from finite sets; run_id stays in JSON logs only."""

    def __init__(self, environment: str,
                 registry: CollectorRegistry = REGISTRY):
        self._environment = environment
        self._registry = registry
        self._messages = Counter(
            "frameflow_worker_messages",
            "Worker messages completed by bounded outcome",
            ("environment", "outcome"), registry=registry)
        for outcome in _OUTCOMES:
            self._messages.labels(environment=environment, outcome=outcome)
        self._processing = Histogram(
            "frameflow_worker_processing_seconds",
            "Worker message processing duration",
            ("environment",),
            buckets=(0.1, 0.25, 0.5, 1, 2, 5, 10, 30, 60, 120, 300),
            registry=registry)
        self._retries = Counter(
            "frameflow_worker_republished_messages",
            "Messages republished with incremented delivery attempt",
            ("environment",), registry=registry)
        self._ready = Gauge(
            "frameflow_worker_ready",
            "1 while the RabbitMQ consumer loop is registered",
            ("environment",), registry=registry)
        self._inflight = Gauge(
            "frameflow_worker_inflight",
            "Messages currently executing in this worker process",
            ("environment",), registry=registry)

    @staticmethod
    def now() -> float:
        return perf_counter()

    def begin_message(self) -> float:
        self._inflight.labels(environment=self._environment).inc()
        return self.now()

    def finish_message(self, started: float, outcome: str) -> None:
        if outcome not in _OUTCOMES:
            raise ValueError(f"unsupported metric outcome: {outcome}")
        self._messages.labels(environment=self._environment, outcome=outcome).inc()
        self._processing.labels(environment=self._environment).observe(
            max(0.0, self.now() - started))
        self._inflight.labels(environment=self._environment).dec()

    def record_republish(self) -> None:
        self._retries.labels(environment=self._environment).inc()

    def set_ready(self, ready: bool) -> None:
        self._ready.labels(environment=self._environment).set(1 if ready else 0)

    def serve(self, host: str, port: int) -> None:
        if not 1 <= port <= 65535:
            raise ValueError("metrics port must be 1..65535")
        # ★ 核心：指标 HTTP server 与消费循环分线程，Prometheus 抓取不会阻塞
        # Rabbit ACK；若把抓取放进消费回调，监控慢会反向制造队列积压。
        start_http_server(port=port, addr=host, registry=self._registry)
