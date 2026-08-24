from frameflow_ai.config import Config


def test_rabbit_vhost_defaults_to_root(monkeypatch):
    monkeypatch.delenv("FRAMEFLOW_RABBITMQ_VHOST", raising=False)

    assert Config.from_env().rabbit_vhost == "/"


def test_rabbit_vhost_comes_from_environment(monkeypatch):
    monkeypatch.setenv("FRAMEFLOW_RABBITMQ_VHOST", "/frameflow-staging")

    assert Config.from_env().rabbit_vhost == "/frameflow-staging"


def test_observability_config_comes_from_environment(monkeypatch):
    monkeypatch.setenv("FRAMEFLOW_ENV", "staging")
    monkeypatch.setenv("FRAMEFLOW_METRICS_HOST", "127.0.0.1")
    monkeypatch.setenv("FRAMEFLOW_METRICS_PORT", "19108")

    cfg = Config.from_env()

    assert cfg.environment == "staging"
    assert cfg.metrics_host == "127.0.0.1"
    assert cfg.metrics_port == 19108


def test_metrics_host_defaults_to_loopback(monkeypatch):
    monkeypatch.delenv("FRAMEFLOW_METRICS_HOST", raising=False)

    assert Config.from_env().metrics_host == "127.0.0.1"
