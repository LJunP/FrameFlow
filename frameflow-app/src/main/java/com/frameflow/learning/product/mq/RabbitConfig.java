package com.frameflow.learning.product.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑：任务交换机 + 主队列（带死信）+ 死信交换机/队列。
 *
 * ★ 核心：DLQ 的意义——反复失败的消息不停留在主队列（否则会阻塞
 * 后面的任务），也不被丢弃（否则静默丢单），而是进"停尸房"等待
 * 人工排查后重放。这是"故障可恢复"与"故障可观察"的交汇点。
 */
@Configuration
public class RabbitConfig {

    public static final String TASK_EXCHANGE = "frameflow.analysis";
    public static final String TASK_QUEUE = "frameflow.analysis.tasks";
    public static final String TASK_ROUTING_KEY = "analyze";
    public static final String DLX_EXCHANGE = "frameflow.dlx";
    public static final String DLQ_QUEUE = "frameflow.analysis.dead";

    @Bean
    DirectExchange taskExchange() {
        return new DirectExchange(TASK_EXCHANGE);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    @Bean
    Queue taskQueue() {
        // 主队列声明死信去向：被 reject/nack(requeue=false) 或过期的消息
        // 自动转入 DLX（routing key 用固定值，便于 DLQ 绑定）
        return QueueBuilder.durable(TASK_QUEUE)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey("analysis.dead")
                .build();
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    @Bean
    Binding taskBinding(DirectExchange taskExchange, Queue taskQueue) {
        return BindingBuilder.bind(taskQueue).to(taskExchange).with(TASK_ROUTING_KEY);
    }

    @Bean
    Binding deadLetterBinding(DirectExchange deadLetterExchange, Queue deadLetterQueue) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with("analysis.dead");
    }

    /** 消息体 JSON 序列化（默认 Java 序列化跨语言不可用，worker 是 Python）。 */
    @Bean
    MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /** 供运维接口查询队列深度/被动声明队列。 */
    @Bean
    org.springframework.amqp.rabbit.core.RabbitAdmin rabbitAdmin(
            org.springframework.amqp.rabbit.connection.ConnectionFactory cf) {
        return new org.springframework.amqp.rabbit.core.RabbitAdmin(cf);
    }
}
