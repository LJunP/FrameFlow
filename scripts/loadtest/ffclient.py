"""FrameFlow 压测用的最小 HTTP 客户端。

只用标准库，与仓库内其它脚本保持一致（不引入 requests）。
所有请求都记录耗时，因为阶段耗时分解是压测的主要产出之一。
"""

from __future__ import annotations

import json
import ssl
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from typing import Any


class ApiError(RuntimeError):
    def __init__(self, status: int, body: str, url: str) -> None:
        super().__init__(f"HTTP {status} {url}: {body[:400]}")
        self.status = status
        self.body = body
        self.url = url


@dataclass
class Timed:
    """一次调用的结果与墙钟耗时。

    压测量的是用户感知的端到端时间，所以一律用墙钟，不用服务端自报的指标。
    """

    value: Any
    seconds: float
    status: int = 200


@dataclass
class Client:
    base_url: str
    token: str | None = None
    timeout: float = 60.0
    admin_key: str | None = None
    _ctx: ssl.SSLContext | None = field(default=None, repr=False)

    def _request(
        self,
        method: str,
        path: str,
        *,
        body: Any = None,
        raw_body: bytes | None = None,
        headers: dict[str, str] | None = None,
        absolute: bool = False,
        expect_json: bool = True,
    ) -> Timed:
        url = path if absolute else f"{self.base_url.rstrip('/')}{path}"
        hdrs = dict(headers or {})
        data: bytes | None = raw_body
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            hdrs.setdefault("Content-Type", "application/json")
        if self.token and not absolute:
            hdrs.setdefault("Authorization", f"Bearer {self.token}")
        if self.admin_key and "/admin/" in path:
            hdrs.setdefault("X-Admin-Key", self.admin_key)

        req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
        start = time.perf_counter()
        try:
            with urllib.request.urlopen(req, timeout=self.timeout, context=self._ctx) as resp:
                payload = resp.read()
                elapsed = time.perf_counter() - start
                status = resp.status
                if not expect_json:
                    return Timed(dict(resp.headers), elapsed, status)
                if not payload:
                    return Timed(None, elapsed, status)
                return Timed(json.loads(payload.decode("utf-8")), elapsed, status)
        except urllib.error.HTTPError as exc:  # 4xx/5xx 是被测量的信号，不是意外
            elapsed = time.perf_counter() - start
            detail = exc.read().decode("utf-8", "replace")
            raise ApiError(exc.code, detail, url) from None
        except urllib.error.URLError as exc:
            raise ApiError(0, f"{exc.reason}", url) from None

    # ── 便捷方法 ──────────────────────────────────────────────
    def get(self, path: str, **kw: Any) -> Timed:
        return self._request("GET", path, **kw)

    def post(self, path: str, body: Any = None, **kw: Any) -> Timed:
        return self._request("POST", path, body=body, **kw)

    def put_bytes(self, url: str, payload: bytes, content_type: str = "application/octet-stream") -> Timed:
        """向预签名 URL 直传字节。不带 Authorization —— 预签名 URL 自带凭证。"""
        return self._request(
            "PUT",
            url,
            raw_body=payload,
            headers={"Content-Type": content_type},
            absolute=True,
            expect_json=False,
        )

    # ── 领域方法 ──────────────────────────────────────────────
    def login(self, email: str, password: str) -> Timed:
        res = self.post("/api/v1/auth/login", {"email": email, "password": password})
        token = (res.value or {}).get("accessToken") or (res.value or {}).get("access_token")
        if not token:
            raise ApiError(200, f"登录成功但响应里没有 accessToken: {res.value}", "/api/v1/auth/login")
        self.token = token
        return res

    def mq_stats(self) -> dict[str, int]:
        """队列深度。未配置 X-Admin-Key 时返回 401，调用方决定降级。"""
        res = self.get("/api/v1/admin/mq/stats")
        raw = res.value or {}
        out: dict[str, int] = {}
        for key in ("taskQueueDepth", "dlqDepth"):
            try:
                out[key] = int(raw.get(key))
            except (TypeError, ValueError):
                out[key] = -1  # 明确表示「读不到」，不要伪装成 0
        return out

    def progress(self, batch_id: int) -> dict[str, int]:
        return dict((self.get(f"/api/v1/batches/{batch_id}/progress").value) or {})
