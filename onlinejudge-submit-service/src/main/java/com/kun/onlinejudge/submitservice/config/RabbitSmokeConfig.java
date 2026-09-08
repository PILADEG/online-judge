package com.kun.onlinejudge.submitservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitSmokeConfig {

    public static final String EXCHANGE = "code-exchange";
    public static final String QUEUE = "code-queue";
    public static final String ROUTING_KEY = "code.routing.key";

    @Bean
    public DirectExchange codeExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue codeQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding codeBinding(DirectExchange codeExchange, Queue codeQueue) {
        return BindingBuilder.bind(codeQueue).to(codeExchange).with(ROUTING_KEY);
    }
}
