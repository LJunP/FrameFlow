package com.frameflow.learning.product.service;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.learning.product.mq.AnalysisTaskMessage;
import com.frameflow.learning.product.mq.RabbitConfig;
import com.frameflow.learning.product.repo.AnalysisOutboxMapper;
import com.frameflow.learning.product.repo.AnalysisOutboxMapper.OutboxRow;
import com.frameflow.learning.shared.error.ApiException;
import com.frameflow.learning.shared.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 把已提交的 outbox 行发到 RabbitMQ。事务提交后立即跑一轮，定时再扫漏网。
 */
@Component
public class AnalysisOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(AnalysisOutboxRelay.class);

    private final AnalysisOutboxMapper outbox;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public AnalysisOutboxRelay(AnalysisOutboxMapper outbox, RabbitTemplate rabbitTemplate,
                               ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 2000)
    public void publishPending() {
        for (OutboxRow row : outbox.listUnpublished(50)) {
            try {
                AnalysisTaskMessage message = objectMapper.readValue(row.getPayload(), AnalysisTaskMessage.class);
                Boolean confirmed = rabbitTemplate.invoke(operation -> {
                    operation.convertAndSend(
                            RabbitConfig.TASK_EXCHANGE, RabbitConfig.TASK_ROUTING_KEY, message);
                    return operation.waitForConfirms(5_000);
                });
                if (!Boolean.TRUE.equals(confirmed)) {
                    throw new ApiException(ErrorCode.INTERNAL_ERROR, "broker 未确认 outbox runId=" + row.getRunId());
                }
                outbox.markPublished(row.getId(), OffsetDateTime.now());
            } catch (Exception e) {
                log.warn("outbox publish failed id={} runId={}: {}", row.getId(), row.getRunId(), e.toString());
            }
        }
    }
}
