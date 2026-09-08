package com.kun.onlinejudge.judgeservice.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SmokeListener {

    @RabbitListener(queues = "code-queue")
    public void onMessage(String message) {
        log.info("[judge-smoke] received from code-queue: {}", message);
    }
}
