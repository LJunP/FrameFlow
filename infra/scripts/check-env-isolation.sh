#!/usr/bin/env bash
set -euo pipefail

# ★ 核心：env 文件按数据解析，绝不 source；否则恶意 `$()` 会在“校验配置”时执行。
# 校验默认 fail-closed，只有 --template 才允许 CHANGE_ME/示例域名/全零 Digest。
exec python3 - "$@" <<'PY'
from __future__ import annotations

import argparse
import json
import os
import pathlib
import re
import stat
import sys
from urllib.parse import urlparse

VALID_ENVS = ("local", "dev", "staging", "production")
SECRET_KEYS = (
    "FRAMEFLOW_DB_PASSWORD", "FRAMEFLOW_REDIS_PASSWORD",
    "FRAMEFLOW_RABBITMQ_PASSWORD", "FRAMEFLOW_STORAGE_SECRET_KEY",
    "FRAMEFLOW_WORKER_KEY",
)
REMOTE_REQUIRED = (
    "FRAMEFLOW_APP_IMAGE", "FRAMEFLOW_WORKER_IMAGE", "FRAMEFLOW_WEB_IMAGE",
    "FRAMEFLOW_DOMAIN", "FRAMEFLOW_MEDIA_DOMAIN",
    "FRAMEFLOW_STORAGE_PUBLIC_ENDPOINT", "FRAMEFLOW_STORAGE_CORS_ORIGINS",
    "FRAMEFLOW_WEB_BIND_PORT", "FRAMEFLOW_MINIO_BIND_PORT",
    "FRAMEFLOW_GRAFANA_PORT", "FRAMEFLOW_LOKI_PORT",
    "FRAMEFLOW_PROMETHEUS_PORT", "FRAMEFLOW_ALERTMANAGER_PORT",
    "FRAMEFLOW_ALERT_SINK_PORT", "FRAMEFLOW_ALLOY_SYSLOG_PORT",
    "FRAMEFLOW_NGINX_LOG_DIR",
    "FRAMEFLOW_JWT_PRIVATE_KEY_HOST_FILE", "FRAMEFLOW_JWT_PUBLIC_KEY_HOST_FILE",
    "FRAMEFLOW_WORKER_PROVIDER_ENV_FILE",
    "FRAMEFLOW_TLS_CERT_NAME", "FRAMEFLOW_CERTBOT_EMAIL",
)
IMAGE_RE = re.compile(r"^[a-z0-9][a-z0-9._/-]*(?::[A-Za-z0-9_.-]+)@sha256:[0-9a-f]{64}$")
PROVIDER_KEY_RE = re.compile(r"^[A-Za-z0-9._~+/=:-]{16,}$")
PROVIDER_ENV_NAME_RE = re.compile(r"^[A-Z_][A-Z0-9_]*$")
SUPPORTED_SEMANTIC_PROVIDERS = {"openai-compat", "openai-responses"}


def die(message: str) -> None:
    raise ValueError(message)


def read_env(path: pathlib.Path) -> dict[str, str]:
    if not path.is_file():
        die(f"env file not found: {path}")
    result: dict[str, str] = {}
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export ") or "=" not in line:
            die(f"{path}:{number}: only KEY=value lines are allowed")
        key, value = line.split("=", 1)
        key = key.strip()
        if not re.fullmatch(r"[A-Z][A-Z0-9_]*", key):
            die(f"{path}:{number}: invalid key {key!r}")
        if key in result:
            die(f"{path}:{number}: duplicate key {key}")
        if "\x00" in value or "\n" in value or "\r" in value:
            die(f"{path}:{number}: invalid control character")
        result[key] = value.strip()
    return result


def require(data: dict[str, str], key: str) -> str:
    value = data.get(key, "")
    if not value:
        die(f"missing required value: {key}")
    return value


def validate_provider_injection(data: dict[str, str], template: bool) -> None:
    """把无密钥目录与 Worker-only Secret 文件绑定，且不执行任一 env value。"""
    raw_catalog = data.get("FRAMEFLOW_SEMANTIC_MODEL_CATALOG_JSON", "").strip()
    provider_path_text = data.get("FRAMEFLOW_WORKER_PROVIDER_ENV_FILE", "").strip()
    required_keys: set[str] = set()

    if raw_catalog:
        try:
            catalog = json.loads(raw_catalog)
        except (json.JSONDecodeError, TypeError) as exc:
            raise ValueError("FRAMEFLOW_SEMANTIC_MODEL_CATALOG_JSON is not valid JSON") from exc
        if not isinstance(catalog, dict) or set(catalog) != {"defaultModelId", "models"}:
            die("semantic model catalog root does not match the strict contract")
        models = catalog.get("models")
        if not isinstance(models, list) or not models:
            die("semantic model catalog models must be a non-empty array")
        for index, model in enumerate(models):
            if not isinstance(model, dict):
                die(f"semantic model catalog models[{index}] must be an object")
            provider = model.get("provider")
            if provider not in SUPPORTED_SEMANTIC_PROVIDERS:
                die(f"semantic model catalog models[{index}] uses an unsupported provider")
            enabled = model.get("enabled")
            if not isinstance(enabled, bool):
                die(f"semantic model catalog models[{index}].enabled must be boolean")
            env_name = model.get("apiKeyEnv")
            if not isinstance(env_name, str) or not PROVIDER_ENV_NAME_RE.fullmatch(env_name):
                die(f"semantic model catalog models[{index}].apiKeyEnv is invalid")
            if enabled:
                required_keys.add(env_name)
        if not required_keys:
            die("semantic model catalog must have at least one enabled model key")

    if provider_path_text and not pathlib.Path(provider_path_text).is_absolute():
        die("FRAMEFLOW_WORKER_PROVIDER_ENV_FILE must be an absolute path")
    if template:
        return

    # 空目录允许纯离线部署，但 Compose 的真实 Provider 必须使用共享目录。否则
    # Worker-only legacy model 与 Java 展示的 fallback model 可能漂移。
    if not raw_catalog:
        if provider_path_text and pathlib.Path(provider_path_text).exists():
            die("Worker Provider env file requires a shared semantic model catalog")
        return
    elif not provider_path_text:
        die("enabled semantic catalog requires FRAMEFLOW_WORKER_PROVIDER_ENV_FILE")

    provider_path = pathlib.Path(provider_path_text)
    try:
        metadata = provider_path.lstat()
    except FileNotFoundError as exc:
        raise ValueError("Worker Provider env file not found") from exc
    if stat.S_ISLNK(metadata.st_mode) or not stat.S_ISREG(metadata.st_mode):
        die("Worker Provider env file must be a regular non-symlink file")
    if stat.S_IMODE(metadata.st_mode) != 0o600:
        die("Worker Provider env file mode must be 0600")
    if metadata.st_uid != os.geteuid():
        die("Worker Provider env file must be owned by the deploying user")

    provider_values = read_env(provider_path)
    if set(provider_values) != required_keys:
        die("Worker Provider env file keys must exactly match the enabled catalog")
    for key, value in provider_values.items():
        if not PROVIDER_KEY_RE.fullmatch(value):
            die(f"{key} must be a non-empty provider token without shell metacharacters")


def validate(environment: str, path: pathlib.Path, template: bool) -> dict[str, str]:
    data = read_env(path)
    if environment not in VALID_ENVS:
        die(f"unsupported environment: {environment}")
    if require(data, "FRAMEFLOW_ENV") != environment:
        die("FRAMEFLOW_ENV does not match --environment")
    if require(data, "COMPOSE_PROJECT_NAME") != f"frameflow-{environment}":
        die(f"COMPOSE_PROJECT_NAME must be frameflow-{environment}")

    suffix = environment.replace("-", "_")
    expected = {
        "FRAMEFLOW_POSTGRES_DB": f"frameflow_{suffix}",
        "FRAMEFLOW_RABBITMQ_VHOST": f"/frameflow-{environment}",
        "FRAMEFLOW_STORAGE_BUCKET": f"frameflow-media-{environment}",
    }
    for key, wanted in expected.items():
        if require(data, key) != wanted:
            die(f"{key} must be {wanted!r}")

    validate_provider_injection(data, template)

    if environment == "local":
        return data

    for key in REMOTE_REQUIRED:
        require(data, key)

    domain = data["FRAMEFLOW_DOMAIN"]
    media_domain = data["FRAMEFLOW_MEDIA_DOMAIN"]
    if domain == media_domain or not re.fullmatch(r"[a-z0-9.-]+", domain + media_domain):
        die("application/media domains must be distinct lower-case hostnames")
    public = urlparse(data["FRAMEFLOW_STORAGE_PUBLIC_ENDPOINT"])
    if public.scheme != "https" or public.hostname != media_domain or public.path not in ("", "/"):
        die("FRAMEFLOW_STORAGE_PUBLIC_ENDPOINT must be https://FRAMEFLOW_MEDIA_DOMAIN")
    if data["FRAMEFLOW_STORAGE_CORS_ORIGINS"] != f"https://{domain}":
        die("FRAMEFLOW_STORAGE_CORS_ORIGINS must contain only the current app origin")
    if data.get("FRAMEFLOW_TLS_CERT_NAME") != domain:
        die("FRAMEFLOW_TLS_CERT_NAME must equal FRAMEFLOW_DOMAIN")
    email = data["FRAMEFLOW_CERTBOT_EMAIL"]
    if not re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+", email):
        die("FRAMEFLOW_CERTBOT_EMAIL is invalid")
    if not template and email.endswith("@example.com"):
        die("FRAMEFLOW_CERTBOT_EMAIL still uses an example address")

    port_keys = (
        "FRAMEFLOW_WEB_BIND_PORT", "FRAMEFLOW_MINIO_BIND_PORT",
        "FRAMEFLOW_GRAFANA_PORT", "FRAMEFLOW_LOKI_PORT",
        "FRAMEFLOW_PROMETHEUS_PORT", "FRAMEFLOW_ALERTMANAGER_PORT",
        "FRAMEFLOW_ALERT_SINK_PORT", "FRAMEFLOW_ALLOY_SYSLOG_PORT",
    )
    for key in port_keys:
        try:
            port = int(data[key])
        except ValueError:
            die(f"{key} must be an integer")
        if not 1024 <= port <= 65535:
            die(f"{key} must be an unprivileged network port")
    if len({data[key] for key in port_keys}) != len(port_keys):
        die("all environment loopback/UDP ports must be distinct")
    if data["FRAMEFLOW_JWT_PRIVATE_KEY_HOST_FILE"] == data["FRAMEFLOW_JWT_PUBLIC_KEY_HOST_FILE"]:
        die("JWT private/public key host files must be different paths")
    if data["FRAMEFLOW_NGINX_LOG_DIR"] != f"/var/log/nginx/frameflow-{environment}":
        die("FRAMEFLOW_NGINX_LOG_DIR must be the current environment's dedicated directory")

    for key in ("FRAMEFLOW_APP_IMAGE", "FRAMEFLOW_WORKER_IMAGE", "FRAMEFLOW_WEB_IMAGE"):
        image = data[key]
        if ":latest" in image or not IMAGE_RE.fullmatch(image):
            die(f"{key} must contain an explicit tag and sha256 digest")
        if not template and ("owner/repository" in image or image.endswith("0" * 64)):
            die(f"{key} still contains a template repository/digest")

    secret_values: list[str] = []
    for key in SECRET_KEYS:
        value = require(data, key)
        if template:
            continue
        if len(value) < 24 or re.search(r"(?i)(change[_-]?me|example|local[_-]?only)", value):
            die(f"{key} must be a non-template secret of at least 24 characters")
        secret_values.append(value)
    if not template and len(secret_values) != len(set(secret_values)):
        die("credentials must not reuse the same secret value")

    if not template:
        for key in ("FRAMEFLOW_DOMAIN", "FRAMEFLOW_MEDIA_DOMAIN"):
            if data[key].endswith(".example.com"):
                die(f"{key} still uses the example domain")
        for key in ("FRAMEFLOW_JWT_PRIVATE_KEY_HOST_FILE", "FRAMEFLOW_JWT_PUBLIC_KEY_HOST_FILE"):
            if not pathlib.PurePosixPath(data[key]).is_absolute():
                die(f"{key} must be an absolute host path")
    return data


parser = argparse.ArgumentParser()
parser.add_argument("--environment", choices=VALID_ENVS)
parser.add_argument("--env-file", type=pathlib.Path)
parser.add_argument("--template", action="store_true")
parser.add_argument("--matrix", nargs="+", type=pathlib.Path)
args = parser.parse_args(sys.argv[1:])

try:
    if args.matrix:
        rows: list[tuple[pathlib.Path, dict[str, str]]] = []
        for path in args.matrix:
            raw = read_env(path)
            environment = require(raw, "FRAMEFLOW_ENV")
            rows.append((path, validate(environment, path, args.template)))
        if len({row[1]["FRAMEFLOW_ENV"] for row in rows}) != len(rows):
            die("matrix contains the same environment more than once")
        unique_keys = (
            "COMPOSE_PROJECT_NAME", "FRAMEFLOW_POSTGRES_DB", "FRAMEFLOW_DB_USERNAME",
            "FRAMEFLOW_RABBITMQ_USER", "FRAMEFLOW_RABBITMQ_VHOST",
            "FRAMEFLOW_STORAGE_ACCESS_KEY", "FRAMEFLOW_STORAGE_BUCKET",
            "FRAMEFLOW_DOMAIN", "FRAMEFLOW_MEDIA_DOMAIN", "FRAMEFLOW_WEB_BIND_PORT",
            "FRAMEFLOW_MINIO_BIND_PORT", "FRAMEFLOW_JWT_PRIVATE_KEY_HOST_FILE",
            "FRAMEFLOW_JWT_PUBLIC_KEY_HOST_FILE", "FRAMEFLOW_NGINX_LOG_DIR",
            "FRAMEFLOW_GRAFANA_PORT", "FRAMEFLOW_LOKI_PORT",
            "FRAMEFLOW_PROMETHEUS_PORT", "FRAMEFLOW_ALERTMANAGER_PORT",
            "FRAMEFLOW_ALERT_SINK_PORT", "FRAMEFLOW_ALLOY_SYSLOG_PORT",
        ) + (() if args.template else SECRET_KEYS)
        for key in unique_keys:
            values = [row[1].get(key, "") for row in rows]
            if len(values) != len(set(values)):
                die(f"matrix isolation failure: duplicate {key}")
        print("env isolation matrix: PASS (" + ", ".join(row[1]["FRAMEFLOW_ENV"] for row in rows) + ")")
    else:
        if not args.environment or not args.env_file:
            parser.error("--environment and --env-file are required without --matrix")
        validate(args.environment, args.env_file, args.template)
        mode = "template" if args.template else "deploy"
        print(f"env isolation: PASS ({args.environment}, {mode})")
except ValueError as exc:
    print(f"env isolation: FAIL: {exc}", file=sys.stderr)
    sys.exit(1)
PY
