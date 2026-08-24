from frameflow_ai.config import Config


def test_rabbit_vhost_defaults_to_root(monkeypatch):
    monkeypatch.delenv("FRAMEFLOW_RABBITMQ_VHOST", raising=False)

    assert Config.from_env().rabbit_vhost == "/"


def test_rabbit_vhost_comes_from_environment(monkeypatch):
    monkeypatch.setenv("FRAMEFLOW_RABBITMQ_VHOST", "/frameflow-staging")

    assert Config.from_env().rabbit_vhost == "/frameflow-staging"
