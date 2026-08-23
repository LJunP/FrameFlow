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
TASK_EXCHANGE = "frameflow.analysis"
TASK_ROUTING_KEY = "analyze"


def on_message(task_body: dict, delivery_attempt: int, cfg: Config,
               client: ReportClient, download, worker_version: str) -> str:
    """处理一条消息，返回 ack 动作：'ack' | 'requeue' | 'dead'。

    delivery_attempt 来源约定（★ 修 bug）：消息体里的 deliveryAttempt 字段。
    broker 原生 nack(requeue=True) 不携带重投计数（x-death 只在死信时写入），
    所以 requeue 分支改为"重发 attempt+1 的新消息 + ack 旧消息"，
    否则毒消息上限永远不会触发（曾是被单测掩盖的真 bug）。
    """
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


def _handle_message(ch, method, properties, body: bytes,
                     cfg: Config, client: ReportClient, download,
                     worker_version: str):
    """消费回调（模块级以便单测 requeue 的重发计数行为）。"""
    task = json.loads(body)
    attempt = int(task.get("deliveryAttempt", 1) or 1)
    action = on_message(task, attempt, cfg, client, download, worker_version)
    if action == "ack":
        ch.basic_ack(delivery_tag=method.delivery_tag)
    elif action == "requeue":
        # ★ 重投计数：不是 nack(requeue)（不携带计数），而是发布一条
        # attempt+1 的新消息后 ack 旧消息——既保住"至少一次"，又让
        # 毒消息上限可判定
        task["deliveryAttempt"] = attempt + 1
        ch.basic_publish(
            exchange=TASK_EXCHANGE, routing_key=TASK_ROUTING_KEY,
            body=json.dumps(task).encode(),
            properties=pika.BasicProperties(
                delivery_mode=2,
                headers={"x-delivery-attempt": attempt + 1}))
        ch.basic_ack(delivery_tag=method.delivery_tag)
    else:
        ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)


def start_worker(cfg: Config, download, worker_version: str):
    """阻塞式消费主循环（main 与测试共用 on_message，循环本身薄）。"""
    client = ReportClient(cfg.api_base, cfg.worker_key)
    conn = pika.BlockingConnection(pika.ConnectionParameters(
        host=cfg.rabbit_host, port=cfg.rabbit_port,
        credentials=pika.PlainCredentials(cfg.rabbit_user, cfg.rabbit_password),
    ))
    channel = conn.channel()
    # ★ 声明参数必须与 Java 侧 RabbitConfig 完全一致（含死信配置）——
    # 队列已存在时 broker 会做参数比对，不一致直接 PRECONDITION_FAILED
    # 关闭信道（曾因漏死信参数在"本地复用过的 RabbitMQ"上启动失败；
    # 测试环境队列每次新建，从未暴露）
    channel.queue_declare(
        queue=TASK_QUEUE, durable=True,
        arguments={
            "x-dead-letter-exchange": "frameflow.dlx",
            "x-dead-letter-routing-key": "analysis.dead",
        })
    channel.basic_qos(prefetch_count=cfg.prefetch)

    def handle(ch, method, properties, body: bytes):
        _handle_message(ch, method, properties, body, cfg, client, download, worker_version)

    channel.basic_consume(queue=TASK_QUEUE, on_message_callback=handle)
    log.info("worker 就绪: queue=%s prefetch=%d", TASK_QUEUE, cfg.prefetch)
    try:
        channel.start_consuming()
    finally:
        conn.close()
