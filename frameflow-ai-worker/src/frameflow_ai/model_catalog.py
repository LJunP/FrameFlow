"""平台托管的语义模型目录。

目录只描述“可选模型如何路由”，绝不承载真实 API Key。每个模型通过
``apiKeyEnv`` 指向 Worker 进程自己的环境变量；解析、选择与 Provider 调用
彼此分离，便于在不触网的情况下验证白名单边界。
"""

from __future__ import annotations

import json
import os
import re
from collections.abc import Mapping
from dataclasses import dataclass
from urllib.parse import urlsplit

CATALOG_ENV = "FRAMEFLOW_SEMANTIC_MODEL_CATALOG_JSON"
LEGACY_MODEL_ID = "platform-default"
LEGACY_API_KEY_ENV = "FRAMEFLOW_SEMANTIC_API_KEY"
SUPPORTED_PROVIDER = "openai-compat"

_TOP_LEVEL_FIELDS = {"defaultModelId", "models"}
_MODEL_FIELDS = {
    "id", "label", "description", "provider", "model", "baseUrl",
    "apiKeyEnv", "enabled",
}
_MODEL_ID = re.compile(r"^[a-z0-9][a-z0-9._-]{0,63}$")
_ENV_NAME = re.compile(r"^[A-Z_][A-Z0-9_]*$")


class ModelCatalogError(ValueError):
    """目录 JSON 或字段契约损坏；调用层须转成逐维度 ERROR Finding。"""


class ModelSelectionError(ValueError):
    """请求了目录之外或已禁用的逻辑模型。"""

    def __init__(self, message: str, model_id: str | None,
                 actual_model: str | None = None):
        super().__init__(message)
        self.model_id = model_id
        self.actual_model = actual_model


@dataclass(frozen=True)
class SemanticModelConfig:
    id: str
    label: str
    description: str
    provider: str
    model: str
    base_url: str
    api_key_env: str
    enabled: bool


@dataclass(frozen=True)
class SemanticModelCatalog:
    default_model_id: str
    models: tuple[SemanticModelConfig, ...]

    def select(self, requested_model_id: object = None) -> SemanticModelConfig:
        """按逻辑 ID 选择启用模型；任何失败都禁止回退到另一模型。"""
        if requested_model_id is None or requested_model_id == "":
            selected_id = self.default_model_id
        elif isinstance(requested_model_id, str):
            selected_id = requested_model_id
        else:
            raise ModelSelectionError("semantic.modelId 必须是字符串", None)

        selected = next((item for item in self.models if item.id == selected_id), None)
        if selected is None:
            raise ModelSelectionError(
                f"模型 {selected_id} 不在平台启用目录中", selected_id)
        if not selected.enabled:
            raise ModelSelectionError(
                f"模型 {selected_id} 已被平台禁用", selected_id, selected.model)
        return selected


def load_model_catalog(
        environment: Mapping[str, str] | None = None) -> SemanticModelCatalog:
    """读取目录；未配置时合成单模型 legacy 目录以保持现有部署兼容。"""
    env = os.environ if environment is None else environment
    raw = env.get(CATALOG_ENV)
    if raw is None:
        return _legacy_catalog(env)
    if not isinstance(raw, str):
        raise ModelCatalogError("模型目录环境变量必须是字符串")
    if not raw.strip():
        return _legacy_catalog(env)
    try:
        payload = json.loads(raw)
    except (json.JSONDecodeError, TypeError) as exc:
        # 不把原始 JSON 写进错误，避免误配时把敏感内容带进 Finding。
        raise ModelCatalogError("模型目录不是合法 JSON") from exc
    return parse_model_catalog(payload)


def parse_model_catalog(payload: object) -> SemanticModelCatalog:
    """严格解析共享目录契约，不接受内嵌 Key 或未声明字段。"""
    if not isinstance(payload, dict):
        raise ModelCatalogError("模型目录顶层必须是 JSON 对象")
    if set(payload) != _TOP_LEVEL_FIELDS:
        raise ModelCatalogError("模型目录顶层字段不符合契约")

    default_model_id = _required_string(payload, "defaultModelId", "模型目录")
    raw_models = payload.get("models")
    if not isinstance(raw_models, list) or not raw_models:
        raise ModelCatalogError("模型目录 models 必须是非空数组")

    models: list[SemanticModelConfig] = []
    seen_ids: set[str] = set()
    for index, raw_model in enumerate(raw_models):
        where = f"models[{index}]"
        if not isinstance(raw_model, dict):
            raise ModelCatalogError(f"{where} 必须是 JSON 对象")
        # ★ 核心：只接受 apiKeyEnv，不接受 apiKey/secret 等旁路字段；否则
        # 目录本身会变成密钥容器，并可能被日志、配置中心或证据意外复制。
        if set(raw_model) != _MODEL_FIELDS:
            raise ModelCatalogError(f"{where} 字段不符合契约")

        model_id = _required_string(raw_model, "id", where)
        if not _MODEL_ID.fullmatch(model_id):
            raise ModelCatalogError(
                f"{where}.id 仅允许小写字母、数字、点、下划线和短横线，最长 64 位")
        if model_id in seen_ids:
            raise ModelCatalogError(f"模型目录存在重复 id: {model_id}")
        seen_ids.add(model_id)

        provider = _required_string(raw_model, "provider", where)
        if provider != SUPPORTED_PROVIDER:
            raise ModelCatalogError(f"{where}.provider 仅支持 {SUPPORTED_PROVIDER}")
        api_key_env = _required_string(raw_model, "apiKeyEnv", where)
        if not _ENV_NAME.fullmatch(api_key_env):
            raise ModelCatalogError(f"{where}.apiKeyEnv 不是合法环境变量名")
        enabled = raw_model.get("enabled")
        if not isinstance(enabled, bool):
            raise ModelCatalogError(f"{where}.enabled 必须是布尔值")
        description = raw_model.get("description")
        if not isinstance(description, str):
            raise ModelCatalogError(f"{where}.description 必须是字符串")

        base_url = _required_string(raw_model, "baseUrl", where)
        _validate_base_url(base_url, where)
        models.append(SemanticModelConfig(
            id=model_id,
            label=_required_string(raw_model, "label", where),
            description=description,
            provider=provider,
            model=_required_string(raw_model, "model", where),
            base_url=base_url.rstrip("/"),
            api_key_env=api_key_env,
            enabled=enabled,
        ))

    default_model = next(
        (model for model in models if model.id == default_model_id), None)
    if default_model is None:
        raise ModelCatalogError("defaultModelId 未指向目录中的模型")
    if not default_model.enabled:
        raise ModelCatalogError("defaultModelId 必须指向 enabled 模型")
    return SemanticModelCatalog(default_model_id=default_model_id,
                                models=tuple(models))


def _legacy_catalog(env: Mapping[str, str]) -> SemanticModelCatalog:
    model = env.get("FRAMEFLOW_SEMANTIC_MODEL", "gpt-4o-mini") or "gpt-4o-mini"
    return SemanticModelCatalog(
        default_model_id=LEGACY_MODEL_ID,
        models=(SemanticModelConfig(
            id=LEGACY_MODEL_ID,
            label="平台默认模型",
            description="兼容既有 FRAMEFLOW_SEMANTIC_* 配置",
            provider=SUPPORTED_PROVIDER,
            model=model,
            base_url=env.get("FRAMEFLOW_SEMANTIC_BASE_URL", "").rstrip("/"),
            api_key_env=LEGACY_API_KEY_ENV,
            enabled=True,
        ),),
    )


def _required_string(payload: Mapping[str, object], field: str, where: str) -> str:
    value = payload.get(field)
    if not isinstance(value, str) or not value.strip():
        raise ModelCatalogError(f"{where}.{field} 必须是非空字符串")
    return value.strip()


def _validate_base_url(value: str, where: str) -> None:
    try:
        parsed = urlsplit(value)
        # 访问 ``port`` 会额外校验非法端口文本，保持与 URI 解析失败一致。
        _ = parsed.port
    except ValueError as exc:
        raise ModelCatalogError(f"{where}.baseUrl 必须是合法 URL") from exc
    if (parsed.scheme.lower() not in {"http", "https"}
            or parsed.hostname is None
            or parsed.username is not None
            or parsed.password is not None
            # 与 Java URI.getRawQuery/getRawFragment 对齐：即使只是尾部的
            # 空 `?` / `#` 也拒绝，防止目录地址成为旁路 token 容器。
            or "?" in value
            or "#" in value):
        raise ModelCatalogError(
            f"{where}.baseUrl 必须是无用户信息、查询参数或片段的 http/https URL")
