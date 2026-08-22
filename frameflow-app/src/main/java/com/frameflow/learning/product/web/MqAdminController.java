package com.frameflow.learning.product.web;

import java.util.HashMap;
import java.util.Map;

import com.frameflow.learning.product.mq.RabbitConfig;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * T6 MQ 运维：队列深度报告（积压告警的数据源）与 DLQ 重放。
 * 仅 OWNER 可用（运维敏感操作）。
 */
@RestController
@RequestMapping("/api/v1/admin/mq")
public class MqAdminController {

    private final RabbitAdmin rabbitAdmin;
    private final RabbitTemplate rabbitTemplate;

    public MqAdminController(RabbitAdmin rabbitAdmin, RabbitTemplate rabbitTemplate) {
        this.rabbitAdmin = rabbitAdmin;
        this.rabbitTemplate = rabbitTemplate;
    }

    public record QueueStats(String taskQueueDepth, String dlqDepth) {
    }

    /** 队列深度：主队列积压 + 死信堆积（F10 接告警时的指标来源）。 */
    @GetMapping("/stats")
    public QueueStats stats(@AuthenticationPrincipal Jwt jwt) {
        requireOwner(jwt);
        return new QueueStats(
                String.valueOf(queueDepth(RabbitConfig.TASK_QUEUE)),
                String.valueOf(queueDepth(RabbitConfig.DLQ_QUEUE)));
    }

    /**
     * DLQ 重放：把死信按原 routing key 重新投回主交换机。
     * 重放的消息 deliveryAttempt+1，worker 看到"重试次数超限"会放弃并回写
     * ANALYSIS_ERROR（毒消息兜底），而不是无限循环。
     */
    @PostMapping("/replay-dlq")
    public Map<String, Integer> replayDlq(@AuthenticationPrincipal Jwt jwt,
                                          @RequestParam(defaultValue = "100") int max) {
        requireOwner(jwt);
        int replayed = 0;
        while (replayed < max) {
            Message dead = rabbitTemplate.receive(RabbitConfig.DLQ_QUEUE, 500);
            if (dead == null) {
                break;
            }
            rabbitTemplate.send(RabbitConfig.TASK_EXCHANGE, RabbitConfig.TASK_ROUTING_KEY, dead);
            replayed++;
        }
        Map<String, Integer> result = new HashMap<>();
        result.put("replayed", replayed);
        return result;
    }

    private int queueDepth(String queue) {
        java.util.Properties props = rabbitAdmin.getQueueProperties(queue);
        if (props == null) {
            return -1;
        }
        // RabbitAdmin 约定键名（amqp 3.x 仍是 Properties 形态）
        String count = props.getProperty("QUEUE_MESSAGE_COUNT", "0");
        return Integer.parseInt(count);
    }

    /** admin 接口无团队上下文，角色直接取 JWT 的 role claim（F5 后引入全局角色判定）。 */
    private void requireOwner(Jwt jwt) {
        if (!"OWNER".equals(jwt.getClaim("role"))) {
            throw new com.frameflow.learning.shared.error.ApiException(
                    com.frameflow.learning.shared.error.ErrorCode.FORBIDDEN);
        }
    }
}
