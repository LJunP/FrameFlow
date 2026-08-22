"""worker 入口：python -m frameflow_ai"""

from __future__ import annotations

import logging
import os

from minio import Minio

from . import __version__
from .config import Config
from .consume import start_worker


def make_downloader(cfg: Config):
    """返回 download(object_key, local_path)；MinIO 客户端复用单连接。"""
    endpoint = cfg.storage_endpoint.replace("http://", "").replace("https://", "")
    secure = cfg.storage_endpoint.startswith("https")
    client = Minio(endpoint, access_key=cfg.storage_access_key,
                   secret_key=cfg.storage_secret_key, secure=secure)

    def download(object_key: str, local_path: str) -> None:
        client.fget_object(cfg.storage_bucket, object_key, local_path)

    return download


def main() -> None:
    logging.basicConfig(level=os.environ.get("FRAMEFLOW_LOG_LEVEL", "INFO"),
                        format="%(asctime)s %(levelname)s %(name)s %(message)s")
    cfg = Config.from_env()
    start_worker(cfg, make_downloader(cfg), __version__)


if __name__ == "__main__":
    main()
