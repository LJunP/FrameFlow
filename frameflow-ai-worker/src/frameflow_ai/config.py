"""环境配置：全部来自环境变量（12-factor），本地默认值与 infra/local 对齐。"""

from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Config:
    rabbit_host: str
    rabbit_port: int
    rabbit_user: str
    rabbit_password: str
    rabbit_vhost: str
    storage_endpoint: str
    storage_access_key: str
    storage_secret_key: str
    storage_bucket: str
    api_base: str
    worker_key: str
    # 消费者预取：背压阀门——一次最多持有的未确认任务数。
    # 调大吞吐高但单 worker 崩溃时"在途"任务多；1..4 是稳妥区间。
    prefetch: int
    # 同一条消息的最大尝试次数（含重投）；超过视为毒消息，回写 ANALYSIS_ERROR 后 ack。
    max_delivery_attempt: int

    @classmethod
    def from_env(cls) -> "Config":
        return cls(
            rabbit_host=os.environ.get("FRAMEFLOW_RABBITMQ_HOST", "127.0.0.1"),
            rabbit_port=int(os.environ.get("FRAMEFLOW_RABBITMQ_PORT", "5672")),
            rabbit_user=os.environ.get("FRAMEFLOW_RABBITMQ_USER", "frameflow"),
            rabbit_password=os.environ.get("FRAMEFLOW_RABBITMQ_PASSWORD", "frameflow_local_only"),
            rabbit_vhost=os.environ.get("FRAMEFLOW_RABBITMQ_VHOST", "/"),
            storage_endpoint=os.environ.get("FRAMEFLOW_STORAGE_ENDPOINT", "http://127.0.0.1:9000"),
            storage_access_key=os.environ.get("FRAMEFLOW_STORAGE_ACCESS_KEY", "frameflow"),
            storage_secret_key=os.environ.get("FRAMEFLOW_STORAGE_SECRET_KEY", "frameflow_local_only"),
            storage_bucket=os.environ.get("FRAMEFLOW_STORAGE_BUCKET", "frameflow-media-local"),
            api_base=os.environ.get("FRAMEFLOW_API_BASE", "http://127.0.0.1:18080"),
            worker_key=os.environ.get("FRAMEFLOW_WORKER_KEY", "frameflow-dev-worker-key"),
            prefetch=int(os.environ.get("FRAMEFLOW_WORKER_PREFETCH", "2")),
            max_delivery_attempt=int(os.environ.get("FRAMEFLOW_WORKER_MAX_ATTEMPT", "3")),
        )
