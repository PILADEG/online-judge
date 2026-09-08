package com.kun.onlinejudge.submitservice.controller;

import com.kun.onlinejudge.submitservice.config.RabbitSmokeConfig;
import javax.annotation.Resource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SmokeSendController {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @GetMapping("/submit/smoke/send")
    public String send() {
        String msg = "smoke-" + System.currentTimeMillis();
        rabbitTemplate.convertAndSend(RabbitSmokeConfig.EXCHANGE, RabbitSmokeConfig.ROUTING_KEY, msg);
        return "sent:" + msg;
    }
}
