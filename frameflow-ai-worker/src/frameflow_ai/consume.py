"""RabbitMQ 消费循环（BlockingConnection 版：单进程简单可靠）。

可靠性设计（与 docs/03 F4 对应）：
- prefetch 限流（背压）：一次最多 prefetch 条未确认任务；
- 处理成功 → ack；回写临时失败 → nack(requeue=True) 稍后重试；
- 尝试次数超限（毒消息）→ 回写 ANALYSIS_ERROR 后 ack，任务不无限循环；
- 回写被业务拒绝（4xx）→ nack(requeue=False) 进 DLQ。
"""

from __future__ import annotations

import json
import logging

import pika

from .config import Config
from .pipeline import AnalysisOutcome, analyze
from .report import ReportClient, ReportFailed, ReportRejected

log = logging.getLogger(__name__)

TASK_QUEUE = "frameflow.analysis.tasks"


def on_message(task_body: dict, delivery_attempt: int, cfg: Config,
               client: ReportClient, download, worker_version: str) -> str:
    """处理一条消息，返回 ack 动作：'ack' | 'requeue' | 'dead'。"""
    run_id = task_body.get("runId", -1)

    # 毒消息兜底：超过尝试上限，不再执行检测，直接回写失败并 ack
    if delivery_attempt > cfg.max_delivery_attempt:
        outcome = AnalysisOutcome(
            run_id=run_id, ok=False, worker_version=worker_version,
            error_summary=f"重试超过 {cfg.max_delivery_attempt} 次，判定为不可处理消息")
        try:
            client.submit(outcome.to_payload())
        except Exception:  # noqa: BLE001 回写失败也 ack：任务已诊断，进入下一步靠人工查 DLQ 报表
            log.exception("毒消息回写失败 runId=%s", run_id)
        return "ack"

    try:
        outcome = analyze(task_body, download, worker_version)
        client.submit(outcome.to_payload())
        return "ack"
    except ReportRejected as e:
        log.warning("回写被拒绝，转 DLQ: %s", e)
        return "dead"
    except ReportFailed as e:
        log.warning("回写暂不可用，重回队列: %s", e)
        return "requeue"


def start_worker(cfg: Config, download, worker_version: str):
    """阻塞式消费主循环（main 与测试共用 on_message，循环本身薄）。"""
    client = ReportClient(cfg.api_base, cfg.worker_key)
    conn = pika.BlockingConnection(pika.ConnectionParameters(
        host=cfg.rabbit_host, port=cfg.rabbit_port,
        credentials=pika.PlainCredentials(cfg.rabbit_user, cfg.rabbit_password),
    ))
    channel = conn.channel()
    channel.queue_declare(queue=TASK_QUEUE, durable=True)
    channel.basic_qos(prefetch_count=cfg.prefetch)

    def handle(ch, method, _properties, body: bytes):
        task = json.loads(body)
        attempt = int(method.headers or {}).get("x-delivery-attempt", 1) or 1
        action = on_message(task, attempt, cfg, client, download, worker_version)
        if action == "ack":
            ch.basic_ack(delivery_tag=method.delivery_tag)
        elif action == "requeue":
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=True)
        else:
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

    channel.basic_consume(queue=TASK_QUEUE, on_message_callback=handle)
    log.info("worker 就绪: queue=%s prefetch=%d", TASK_QUEUE, cfg.prefetch)
    try:
        channel.start_consuming()
    finally:
        conn.close()
