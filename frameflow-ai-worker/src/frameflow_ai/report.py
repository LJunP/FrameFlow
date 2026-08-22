"""结果回写客户端：POST 到 Java 内部接口，带 X-Worker-Key 与重试。"""

from __future__ import annotations

import time

import requests


class ReportClient:
    def __init__(self, api_base: str, worker_key: str, timeout_s: float = 10.0):
        self._url = api_base.rstrip("/") + "/api/v1/internal/analysis-results"
        self._headers = {"X-Worker-Key": worker_key}
        self._timeout = timeout_s

    def submit(self, payload: dict, retries: int = 3) -> dict:
        """提交结果；4xx（除 429）说明是请求本身的问题，重试无意义。"""
        last_error = None
        for attempt in range(1, retries + 1):
            try:
                resp = requests.post(self._url, json=payload,
                                     headers=self._headers, timeout=self._timeout)
                if 200 <= resp.status_code < 300:
                    return resp.json()
                if 400 <= resp.status_code < 500 and resp.status_code != 429:
                    # 业务拒绝（如 run 不存在 404）：重试只会重复失败
                    raise ReportRejected(
                        f"服务端拒绝回写: HTTP {resp.status_code} {resp.text[:200]}")
            except requests.RequestException as e:
                last_error = e
            time.sleep(0.5 * attempt)   # 线性退避
        raise ReportFailed(f"回写重试用尽: {last_error}")


class ReportRejected(Exception):
    """4xx：消息本身有问题，应进 DLQ 而不是重试。"""


class ReportFailed(Exception):
    """网络/服务不可用：消息应重回队列稍后再试。"""
