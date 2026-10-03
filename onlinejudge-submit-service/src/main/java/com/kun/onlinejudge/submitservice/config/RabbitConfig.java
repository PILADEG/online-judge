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

    @Bean
    public Queue codeQueue() {
        return QueueBuilder.durable(RabbitConstant.QUEUE)
                .deadLetterExchange(RabbitConstant.DLX_EXCHANGE)
                .deadLetterRoutingKey(RabbitConstant.DLQ_ROUTING_KEY)
                .build();
    }

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
