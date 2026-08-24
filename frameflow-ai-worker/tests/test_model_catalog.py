"""F6.1 平台模型目录、白名单路由与证据边界测试（全离线）。"""

import copy
import json

import pytest
import requests

from frameflow_ai import semantic
from frameflow_ai.model_catalog import (
    CATALOG_ENV,
    LEGACY_MODEL_ID,
    ModelCatalogError,
    load_model_catalog,
    parse_model_catalog,
)
from frameflow_ai.providers import (OpenAICompatProvider,
                                    OpenAIResponsesProvider)


def _catalog() -> dict:
    return {
        "defaultModelId": "balanced",
        "models": [
            {
                "id": "balanced",
                "label": "均衡",
                "description": "平台默认档",
                "provider": "openai-compat",
                "model": "vision-balanced-v2",
                "baseUrl": "https://balanced.internal.example/v1/",
                "apiKeyEnv": "FRAMEFLOW_MODEL_BALANCED_KEY",
                "enabled": True,
            },
            {
                "id": "quality",
                "label": "高质量",
                "description": "复杂终审档",
                "provider": "openai-compat",
                "model": "vision-quality-v5",
                "baseUrl": "https://quality.internal.example/v1",
                "apiKeyEnv": "FRAMEFLOW_MODEL_QUALITY_KEY",
                "enabled": True,
            },
            {
                "id": "retired",
                "label": "已停用",
                "description": "仅保留历史证据解释",
                "provider": "openai-compat",
                "model": "vision-retired-v1",
                "baseUrl": "https://retired.internal.example/v1",
                "apiKeyEnv": "FRAMEFLOW_MODEL_RETIRED_KEY",
                "enabled": False,
            },
            {
                "id": "responses",
                "label": "Responses 协议",
                "description": "Responses API 视觉模型",
                "provider": "openai-responses",
                "model": "vision-responses-v1",
                "baseUrl": "https://responses.internal.example/v1",
                "apiKeyEnv": "FRAMEFLOW_MODEL_RESPONSES_KEY",
                "enabled": True,
            },
        ],
    }


def _install_catalog(monkeypatch, payload: dict | str | None = None) -> None:
    raw = json.dumps(_catalog() if payload is None else payload)
    monkeypatch.setenv(CATALOG_ENV, raw)
    monkeypatch.delenv("FRAMEFLOW_SEMANTIC_PROVIDER", raising=False)


def _evidence(findings: list[dict]) -> list[dict]:
    return [json.loads(item["evidence"]) for item in findings]


def test_unconfigured_catalog_preserves_legacy_platform_default(monkeypatch):
    monkeypatch.delenv(CATALOG_ENV, raising=False)
    monkeypatch.setenv("FRAMEFLOW_SEMANTIC_BASE_URL", "https://legacy.example/v1/")
    monkeypatch.setenv("FRAMEFLOW_SEMANTIC_MODEL", "legacy-vision")

    catalog = load_model_catalog()
    selected = catalog.select()

    assert catalog.default_model_id == LEGACY_MODEL_ID
    assert selected.id == LEGACY_MODEL_ID
    assert selected.model == "legacy-vision"
    assert selected.base_url == "https://legacy.example/v1"
    assert selected.api_key_env == "FRAMEFLOW_SEMANTIC_API_KEY"


def test_router_selects_default_and_explicit_models_from_allowlist(monkeypatch):
    _install_catalog(monkeypatch)
    monkeypatch.setenv("FRAMEFLOW_MODEL_BALANCED_KEY", "balanced-secret")
    monkeypatch.setenv("FRAMEFLOW_MODEL_QUALITY_KEY", "quality-secret")
    monkeypatch.setenv("FRAMEFLOW_MODEL_RESPONSES_KEY", "responses-secret")

    default_provider = semantic.build_provider()
    quality_provider = semantic.build_provider("quality")
    responses_provider = semantic.build_provider("responses")

    assert isinstance(default_provider, OpenAICompatProvider)
    assert (default_provider.model_id, default_provider.model) == (
        "balanced", "vision-balanced-v2")
    assert default_provider.api_key == "balanced-secret"
    assert (quality_provider.model_id, quality_provider.model) == (
        "quality", "vision-quality-v5")
    assert quality_provider.api_key == "quality-secret"
    assert isinstance(responses_provider, OpenAIResponsesProvider)
    assert (responses_provider.model_id, responses_provider.model) == (
        "responses", "vision-responses-v1")
    assert responses_provider.api_key == "responses-secret"


def test_unknown_model_produces_one_error_per_dimension_without_call(monkeypatch):
    _install_catalog(monkeypatch)
    monkeypatch.setattr(
        "frameflow_ai.providers.openai_compat.requests.post",
        lambda *args, **kwargs: pytest.fail("未知模型不得触发 Provider 调用"))

    findings = semantic.run_semantic(
        {"semantic": {"enabled": True, "modelId": "not-listed",
                      "dimensions": ["prompt_alignment", "policy_violation"]}},
        "brief", "video.mp4", [], [], None)

    assert [item["dimension"] for item in findings] == [
        "prompt_alignment", "policy_violation"]
    assert all(item["verdict"] == "ERROR" for item in findings)
    assert all(item["severity"] == "WARNING" for item in findings)
    assert all(evidence["modelId"] == "not-listed" for evidence in _evidence(findings))


def test_disabled_model_is_not_silently_replaced_by_default(monkeypatch):
    _install_catalog(monkeypatch)

    findings = semantic.run_semantic(
        {"semantic": {"enabled": True, "modelId": "retired",
                      "dimensions": ["quality_impression"]}},
        "brief", "video.mp4", [], [], None)
    evidence = _evidence(findings)[0]

    assert findings[0]["verdict"] == "ERROR"
    assert evidence["provider"] == "semantic-model-router"
    assert evidence["modelId"] == "retired"
    assert evidence["model"] == "vision-retired-v1"
    assert "禁用" in evidence["error"]


def test_corrupt_catalog_produces_errors_without_echoing_raw_json(monkeypatch):
    leaked_marker = "must-not-appear-in-evidence"
    monkeypatch.setenv(CATALOG_ENV, "{broken-" + leaked_marker)
    monkeypatch.delenv("FRAMEFLOW_SEMANTIC_PROVIDER", raising=False)

    findings = semantic.run_semantic(
        {"semantic": {"enabled": True, "modelId": "quality",
                      "dimensions": ["prompt_alignment", "quality_impression"]}},
        "brief", "video.mp4", [], [], None)

    assert len(findings) == 2
    assert all(item["verdict"] == "ERROR" for item in findings)
    assert all(json.loads(item["evidence"])["modelId"] == "quality"
               for item in findings)
    assert leaked_marker not in json.dumps(findings, ensure_ascii=False)


def test_catalog_rejects_embedded_api_key_field():
    payload = copy.deepcopy(_catalog())
    payload["models"][0]["apiKey"] = "must-never-live-in-catalog"

    with pytest.raises(ModelCatalogError, match="字段不符合契约"):
        parse_model_catalog(payload)


@pytest.mark.parametrize("invalid_id", [
    "Uppercase",
    "-starts-with-dash",
    "a" * 65,
    "contains space",
])
def test_catalog_model_id_matches_cross_service_contract(invalid_id):
    payload = copy.deepcopy(_catalog())
    payload["models"][0]["id"] = invalid_id
    payload["defaultModelId"] = invalid_id

    with pytest.raises(ModelCatalogError, match="最长 64 位"):
        parse_model_catalog(payload)


@pytest.mark.parametrize("invalid_env", [
    "lowercase_key",
    "FRAMEFLOW-MODEL-KEY",
    "9FRAMEFLOW_MODEL_KEY",
])
def test_catalog_api_key_env_requires_uppercase_environment_name(invalid_env):
    payload = copy.deepcopy(_catalog())
    payload["models"][0]["apiKeyEnv"] = invalid_env

    with pytest.raises(ModelCatalogError, match="环境变量名"):
        parse_model_catalog(payload)


@pytest.mark.parametrize("invalid_url", [
    "ftp://provider.example/v1",
    "https://user:password@provider.example/v1",
    "https://provider.example/v1?api_key=must-not-live-here",
    "https://provider.example/v1?",
    "https://provider.example/v1#private-route",
    "https://provider.example/v1#",
    "provider.example/v1",
])
def test_catalog_base_url_requires_http_without_userinfo(invalid_url):
    payload = copy.deepcopy(_catalog())
    payload["models"][0]["baseUrl"] = invalid_url

    with pytest.raises(ModelCatalogError, match="http/https"):
        parse_model_catalog(payload)


def test_catalog_default_model_must_be_enabled():
    payload = copy.deepcopy(_catalog())
    payload["models"][0]["enabled"] = False

    with pytest.raises(ModelCatalogError, match="enabled"):
        parse_model_catalog(payload)


def test_catalog_provider_rejects_unknown_protocol():
    payload = copy.deepcopy(_catalog())
    payload["models"][0]["provider"] = "fake"

    with pytest.raises(
            ModelCatalogError,
            match="仅支持 openai-compat、openai-responses"):
        parse_model_catalog(payload)


@pytest.mark.parametrize(("field", "invalid_value", "message"), [
    ("label", "  ", "非空字符串"),
    ("provider", "  ", "非空字符串"),
    ("model", "", "非空字符串"),
    ("baseUrl", "", "非空字符串"),
    ("apiKeyEnv", "", "非空字符串"),
    ("description", None, "必须是字符串"),
    ("enabled", "true", "必须是布尔值"),
])
def test_catalog_model_fields_match_java_required_types(
        field, invalid_value, message):
    payload = copy.deepcopy(_catalog())
    payload["models"][0][field] = invalid_value

    with pytest.raises(ModelCatalogError, match=message):
        parse_model_catalog(payload)


def test_catalog_description_may_be_an_explicit_empty_string():
    payload = copy.deepcopy(_catalog())
    payload["models"][0]["description"] = ""

    assert parse_model_catalog(payload).models[0].description == ""


def test_selected_model_success_evidence_records_logical_and_actual_only(monkeypatch):
    _install_catalog(monkeypatch)
    secret_key = "quality-super-secret"
    secret_base_url = "https://quality.internal.example/v1"
    monkeypatch.setenv("FRAMEFLOW_MODEL_QUALITY_KEY", secret_key)
    captured = {}

    class StubResponse:
        def raise_for_status(self):
            return None

        def json(self):
            return {"choices": [{"message": {"content":
                    '{"dimension":"prompt_alignment","verdict":"PASS","reason":"ok"}'}}]}

    def fake_post(url, headers, json, timeout):
        captured.update(url=url, headers=headers, payload=json, timeout=timeout)
        return StubResponse()

    monkeypatch.setattr(
        "frameflow_ai.providers.openai_compat.requests.post", fake_post)
    findings = semantic.run_semantic(
        {"semantic": {"enabled": True, "modelId": "quality",
                      "dimensions": ["prompt_alignment"]}},
        "brief", "video.mp4", [], [], None)
    evidence = _evidence(findings)[0]

    assert captured["url"] == secret_base_url + "/chat/completions"
    assert captured["payload"]["model"] == "vision-quality-v5"
    assert captured["headers"]["Authorization"] == f"Bearer {secret_key}"
    assert evidence["modelId"] == "quality"
    assert evidence["model"] == "vision-quality-v5"
    serialized_evidence = json.dumps(evidence, ensure_ascii=False)
    assert secret_key not in serialized_evidence
    assert secret_base_url not in serialized_evidence
    assert "FRAMEFLOW_MODEL_QUALITY_KEY" not in serialized_evidence


def test_provider_failure_evidence_does_not_leak_key_or_base_url(monkeypatch):
    _install_catalog(monkeypatch)
    secret_key = "balanced-super-secret"
    secret_base_url = "https://balanced.internal.example/v1"
    monkeypatch.setenv("FRAMEFLOW_MODEL_BALANCED_KEY", secret_key)

    def fail_with_sensitive_request_details(*args, **kwargs):
        raise requests.ConnectionError(
            f"failed at {secret_base_url}?api_key={secret_key}")

    monkeypatch.setattr(
        "frameflow_ai.providers.openai_compat.requests.post",
        fail_with_sensitive_request_details)
    findings = semantic.run_semantic(
        {"semantic": {"enabled": True,
                      "dimensions": ["prompt_alignment"]}},
        "brief", "video.mp4", [], [], None)
    evidence = _evidence(findings)[0]

    assert findings[0]["verdict"] == "ERROR"
    assert evidence["modelId"] == "balanced"
    assert evidence["model"] == "vision-balanced-v2"
    serialized = json.dumps(findings, ensure_ascii=False)
    assert secret_key not in serialized
    assert secret_base_url not in serialized


def test_custom_model_never_falls_back_to_legacy_api_key(monkeypatch):
    _install_catalog(monkeypatch)
    monkeypatch.delenv("FRAMEFLOW_MODEL_QUALITY_KEY", raising=False)
    monkeypatch.setenv("FRAMEFLOW_SEMANTIC_API_KEY", "legacy-secret-must-not-be-used")

    provider = semantic.build_provider("quality")
    findings = semantic.run_semantic(
        {"semantic": {"enabled": True, "modelId": "quality",
                      "dimensions": ["prompt_alignment"]}},
        "brief", "video.mp4", [], [], provider)

    assert provider.api_key == ""
    assert findings[0]["verdict"] == "ERROR"
    assert "legacy-secret-must-not-be-used" not in json.dumps(findings)
