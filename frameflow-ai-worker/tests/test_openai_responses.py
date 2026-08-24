"""Responses API 多模态协议适配器测试（全离线 Stub）。"""

import base64
import json
import traceback

import pytest
import requests

from frameflow_ai import semantic
from frameflow_ai.providers import (OpenAIResponsesProvider, ProviderDisabled,
                                    ProviderError, SemanticRequest)


def _request(frames_jpeg=None, dimensions=None) -> SemanticRequest:
    return SemanticRequest(
        brief_content="检查视频是否符合商品展示要求",
        frames_jpeg=[b"jpeg-one"] if frames_jpeg is None else frames_jpeg,
        frame_timecodes_ms=[100, 200, 300, 400],
        dimensions=(dimensions or ["prompt_alignment", "policy_violation"]),
        candidate_hint="candidate-1",
    )


class StubResponse:
    def __init__(self, payload):
        self.payload = payload

    def raise_for_status(self):
        return None

    def json(self):
        return self.payload


def test_openai_responses_disabled_without_key():
    provider = OpenAIResponsesProvider(base_url="", api_key="")

    assert provider.available is False
    with pytest.raises(ProviderDisabled, match="禁用"):
        provider.analyze(_request())


def test_openai_responses_posts_protocol_and_jpegs_byte_for_byte(monkeypatch):
    captured = {}
    response_payload = {
        "output": [
            {"type": "reasoning", "summary": []},
            {
                "type": "message",
                "content": [
                    {"type": "output_text", "text":
                     '说明\n{"dimension":"prompt_alignment",'
                     '"verdict":"pass","reason":"画面一致"}'},
                    {"type": "refusal", "refusal": ""},
                ],
            },
            {
                "type": "message",
                "content": [{"type": "output_text", "text":
                             '{"dimension":"policy_violation",'
                             '"verdict":"VIOLATE","reason":"出现风险"}'}],
            },
        ],
    }

    def fake_post(url, headers, json, timeout):
        captured.update(url=url, headers=headers, payload=json, timeout=timeout)
        return StubResponse(response_payload)

    monkeypatch.setattr(
        "frameflow_ai.providers.openai_responses.requests.post", fake_post)
    source_jpegs = [
        b"\xff\xd8responses-frame-1\xff\xd9",
        b"\xff\xd8responses-frame-2\xff\xd9",
        b"\xff\xd8responses-frame-3\xff\xd9",
        b"\xff\xd8responses-frame-4\xff\xd9",
    ]
    provider = OpenAIResponsesProvider(
        base_url="https://responses.example/v1/", api_key="test-only-key",
        model="vision-responses", model_id="responses-model", timeout_s=7.5)

    result = provider.analyze(_request(source_jpegs))

    assert captured["url"] == "https://responses.example/v1/responses"
    assert captured["headers"] == {"Authorization": "Bearer test-only-key"}
    assert captured["timeout"] == 7.5
    assert captured["payload"]["model"] == "vision-responses"
    assert captured["payload"]["stream"] is False
    assert "temperature" not in captured["payload"]
    assert "messages" not in captured["payload"]
    assert captured["payload"]["input"][0]["role"] == "user"
    content = captured["payload"]["input"][0]["content"]
    assert content[0]["type"] == "input_text"
    assert "关键帧数量: 3" in content[0]["text"]
    image_blocks = [block for block in content
                    if block["type"] == "input_image"]
    assert len(image_blocks) == 3
    decoded = []
    for block in image_blocks:
        data_url = block["image_url"]
        assert data_url.startswith("data:image/jpeg;base64,")
        decoded.append(base64.b64decode(
            data_url.split(",", 1)[1], validate=True))
    assert decoded == source_jpegs[:3]
    assert [(item.dimension, item.verdict) for item in result.verdicts] == [
        ("prompt_alignment", "PASS"),
        ("policy_violation", "VIOLATE"),
    ]
    assert result.raw_output == "\n".join([
        '说明\n{"dimension":"prompt_alignment",'
        '"verdict":"pass","reason":"画面一致"}',
        '{"dimension":"policy_violation",'
        '"verdict":"VIOLATE","reason":"出现风险"}',
    ])


def test_openai_responses_prompt_budget_is_enforced(monkeypatch):
    captured = {}

    def fake_post(url, headers, json, timeout):
        captured["prompt"] = json["input"][0]["content"][0]["text"]
        return StubResponse({"output": [{"content": [{
            "type": "output_text",
            "text": '{"dimension":"prompt_alignment",'
                    '"verdict":"PASS","reason":"ok"}',
        }]}]})

    monkeypatch.setattr(
        "frameflow_ai.providers.openai_responses.requests.post", fake_post)
    provider = OpenAIResponsesProvider(
        base_url="https://responses.example/v1", api_key="test-key",
        max_prompt_chars=80)
    request = SemanticRequest(
        brief_content="很长的要求" * 100,
        frames_jpeg=[], frame_timecodes_ms=[],
        dimensions=["prompt_alignment"])

    provider.analyze(request)

    assert captured["prompt"].endswith("\n...(truncated)")
    assert len(captured["prompt"]) == 80 + len("\n...(truncated)")


@pytest.mark.parametrize("payload", [
    None,
    {},
    {"output": {}},
    {"output": []},
    {"output": [{"content": [{"type": "output_text", "text": 123}]}]},
    {"output": [{"content": [{"type": "other", "text": "not-output"}]}]},
])
def test_openai_responses_corrupt_payload_is_provider_error(monkeypatch, payload):
    monkeypatch.setattr(
        "frameflow_ai.providers.openai_responses.requests.post",
        lambda *args, **kwargs: StubResponse(payload))
    provider = OpenAIResponsesProvider(
        base_url="https://responses.example/v1", api_key="test-key")

    with pytest.raises(ProviderError, match="响应格式无效") as exc_info:
        provider.analyze(_request())

    assert "responses.example" not in str(exc_info.value)
    assert "test-key" not in str(exc_info.value)


@pytest.mark.parametrize(("raised", "expected_message"), [
    (requests.Timeout, "语义模型超时"),
    (requests.ConnectionError, "语义模型调用失败"),
])
def test_openai_responses_failures_are_error_and_redacted(
        monkeypatch, raised, expected_message):
    secret_key = "responses-super-secret"
    secret_base_url = "https://private-responses.example/v1"

    def fail(*args, **kwargs):
        raise raised(f"failed at {secret_base_url}?api_key={secret_key}")

    monkeypatch.setattr(
        "frameflow_ai.providers.openai_responses.requests.post", fail)
    provider = OpenAIResponsesProvider(
        base_url=secret_base_url, api_key=secret_key,
        model="vision-responses", model_id="responses-model", timeout_s=2.0)

    with pytest.raises(ProviderError) as exc_info:
        provider.analyze(_request())
    rendered_exception = "".join(traceback.format_exception(exc_info.value))
    assert expected_message in str(exc_info.value)
    assert secret_key not in rendered_exception
    assert secret_base_url not in rendered_exception

    findings = semantic.run_semantic(
        {"semantic": {"enabled": True,
                      "dimensions": ["prompt_alignment"]}},
        "brief", "video.mp4", [], [], provider)

    assert findings[0]["verdict"] == "ERROR"
    evidence = json.loads(findings[0]["evidence"])
    assert expected_message in evidence["error"]
    assert evidence["provider"] == "semantic-openai-responses"
    assert evidence["modelId"] == "responses-model"
    serialized = json.dumps(findings, ensure_ascii=False)
    assert secret_key not in serialized
    assert secret_base_url not in serialized
