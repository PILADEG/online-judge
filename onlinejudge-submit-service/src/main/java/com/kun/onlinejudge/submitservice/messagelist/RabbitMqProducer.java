package com.kun.onlinejudge.submitservice.messagelist;

import com.kun.onlinejudge.constant.RabbitConstant;
import javax.annotation.Resource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class RabbitMqProducer {
    @Resource
    private RabbitTemplate rabbitTemplate;

    public void send(String message) {
        rabbitTemplate
                .convertAndSend(RabbitConstant.EXCHANGE, RabbitConstant.ROUTING_KEY, message);
    }
}
