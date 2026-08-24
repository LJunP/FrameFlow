import json
import logging

from prometheus_client import CollectorRegistry

from frameflow_ai.observability import JsonFormatter, WorkerMetrics


def test_json_formatter_emits_bounded_context_without_config_or_secret_fields():
    record = logging.LogRecord(
        name="frameflow_ai.consume", level=logging.INFO, pathname=__file__, lineno=1,
        msg="completed", args=(), exc_info=None)
    record.event = "worker_message_completed"
    record.run_id = 42
    record.outcome = "succeeded"

    document = json.loads(JsonFormatter("test").format(record))

    assert document["service"] == "frameflow-worker"
    assert document["environment"] == "test"
    assert document["event"] == "worker_message_completed"
    assert document["run_id"] == 42
    assert "worker_key" not in document
    assert "storage_secret_key" not in document


def test_histogram_and_finite_outcomes_are_pre_registered():
    registry = CollectorRegistry()
    metrics = WorkerMetrics("test", registry)

    started = metrics.begin_message()
    metrics.finish_message(started, "analysis_error")

    assert registry.get_sample_value(
        "frameflow_worker_messages_total",
        {"environment": "test", "outcome": "analysis_error"}) == 1
    assert registry.get_sample_value(
        "frameflow_worker_processing_seconds_count",
        {"environment": "test"}) == 1
    assert registry.get_sample_value(
        "frameflow_worker_messages_total",
        {"environment": "test", "outcome": "report_rejected"}) == 0
