"""消费决策测试：ack / requeue / dead / 毒消息兜底。"""

from frameflow_ai.config import Config
from frameflow_ai.consume import on_message
from frameflow_ai.report import ReportFailed, ReportRejected


def _cfg() -> Config:
    return Config(
        rabbit_host="x", rabbit_port=1, rabbit_user="x", rabbit_password="x",
        storage_endpoint="x", storage_access_key="x", storage_secret_key="x",
        storage_bucket="x", api_base="x", worker_key="x",
        prefetch=2, max_delivery_attempt=3)


class _Client:
    def __init__(self, exc=None):
        self.exc = exc
        self.submitted = []

    def submit(self, payload):
        if self.exc:
            raise self.exc
        self.submitted.append(payload)
        return {"duplicate": False}


def _ok_analyze(task, download, version):
    from frameflow_ai.pipeline import AnalysisOutcome
    return AnalysisOutcome(run_id=task["runId"], ok=True, worker_version=version)


def test_happy_path_acks(monkeypatch):
    monkeypatch.setattr("frameflow_ai.consume.analyze", _ok_analyze)
    client = _Client()
    action = on_message({"runId": 1}, 1, _cfg(), client, None, "t")
    assert action == "ack" and client.submitted[0]["ok"] is True


def test_report_rejected_goes_to_dlq(monkeypatch):
    monkeypatch.setattr("frameflow_ai.consume.analyze", _ok_analyze)
    action = on_message({"runId": 1}, 1, _cfg(), _Client(ReportRejected("404")), None, "t")
    assert action == "dead"


def test_report_failed_requeues(monkeypatch):
    monkeypatch.setattr("frameflow_ai.consume.analyze", _ok_analyze)
    action = on_message({"runId": 1}, 1, _cfg(), _Client(ReportFailed("timeout")), None, "t")
    assert action == "requeue"


def test_poison_message_reports_error_and_acks():
    client = _Client()
    action = on_message({"runId": 5}, 4, _cfg(), client, None, "t")   # attempt > 3
    assert action == "ack"
    assert client.submitted[0]["ok"] is False
    assert "重试超过" in client.submitted[0]["errorSummary"]
