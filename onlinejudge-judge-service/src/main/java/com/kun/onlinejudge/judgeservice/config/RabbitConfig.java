package com.kun.onlinejudge.judgeservice.config;

import com.kun.onlinejudge.constant.RabbitConstant;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public DirectExchange codeExchange() {
        return new DirectExchange(RabbitConstant.EXCHANGE, true, false);
    }

    @Bean
    public Queue codeQueue() {
        return new Queue(RabbitConstant.QUEUE, true);
    }

    @Bean
    public Binding codeBinding(DirectExchange codeExchange, Queue codeQueue) {
        return BindingBuilder.bind(codeQueue).to(codeExchange).with(RabbitConstant.ROUTING_KEY);
    }
}
