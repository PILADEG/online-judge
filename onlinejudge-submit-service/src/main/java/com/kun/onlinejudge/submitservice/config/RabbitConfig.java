package com.kun.onlinejudge.submitservice.config;

import com.kun.onlinejudge.constant.RabbitConstant;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑（与 judge-service 侧保持同源、幂等重复声明）。
 *
 * <p>主队列配了死信交换机：被判题消费者 basicNack(requeue=false) 的消息会转入 code-dlq 存档。
 * 死信队列只做"收容 + 可观测"，不参与重试（重试由 question_submit.status 状态机驱动）。
 */
@Configuration
public class RabbitConfig {

    @Bean
    public DirectExchange codeExchange() {
        return new DirectExchange(RabbitConstant.EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange codeDlxExchange() {
        return new DirectExchange(RabbitConstant.DLX_EXCHANGE, true, false);
    }

    /**
     * 主（判题）队列。
     * ⚠️ 队列参数不可变：给已存在的 code-queue 增加 DLX 参数时，必须先在 broker 上删除该队列
     * （确认无积压消息）再启动服务重建，否则会报 inequivalent arg 'x-dead-letter-exchange'。
     */
    @Bean
    public Queue codeQueue() {
        return QueueBuilder.durable(RabbitConstant.QUEUE)
                .deadLetterExchange(RabbitConstant.DLX_EXCHANGE)
                .deadLetterRoutingKey(RabbitConstant.DLQ_ROUTING_KEY)
                .build();
    }

    /** 死信队列：不配 DLX，避免"死信再死信"的无限循环 */
    @Bean
    public Queue codeDlq() {
        return QueueBuilder.durable(RabbitConstant.DLQ).build();
    }

    @Bean
    public Binding codeBinding(@Qualifier("codeExchange") DirectExchange codeExchange,
                               @Qualifier("codeQueue") Queue codeQueue) {
        return BindingBuilder.bind(codeQueue).to(codeExchange).with(RabbitConstant.ROUTING_KEY);
    }

    @Bean
    public Binding codeDlqBinding(@Qualifier("codeDlxExchange") DirectExchange codeDlxExchange,
                                  @Qualifier("codeDlq") Queue codeDlq) {
        return BindingBuilder.bind(codeDlq).to(codeDlxExchange).with(RabbitConstant.DLQ_ROUTING_KEY);
    }
}
