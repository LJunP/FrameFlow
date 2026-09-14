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
 *
 * ★ 核心（2026-09-13 修复）：这里曾经用「JWT 的 role == OWNER」做授权，
 * 但 OWNER 是**团队作用域**角色，而 MQ 是 vhost 内的**全局**基础设施——
 * 结果任何注册用户（注册即成为自己团队的 OWNER）都能读取全局队列、
 * 并重放**全体团队**的死信。团队角色与平台权限是两回事，不能混用。
 *
 * 现在的授权分两层：
 * 1. 必须已登录（SecurityConfig 的 anyRequest().authenticated()，提供操作者身份）；
 * 2. 必须持有平台管理员密钥 X-Admin-Key（AdminAuthFilter，且未配置时拒绝全部）。
 */
@RestController
@RequestMapping("/api/v1/admin/mq")
public class MqAdminController {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(MqAdminController.class);

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
        // 重放是跨团队的破坏性操作，必须留下"谁在什么时候放了多少条"的痕迹
        int capped = Math.min(Math.max(max, 0), 100);
        log.warn("MQ DLQ 重放 operatorUserId={} max={}", jwt.getSubject(), capped);
        int replayed = 0;
        while (replayed < capped) {
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

}
