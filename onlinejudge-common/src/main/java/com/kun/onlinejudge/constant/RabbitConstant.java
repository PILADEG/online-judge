package com.kun.onlinejudge.constant;

public interface RabbitConstant {

    String EXCHANGE = "code-exchange";
    String QUEUE = "code-queue";
    String ROUTING_KEY = "code.routing.key";

    String DLX_EXCHANGE = "code-dlx-exchange";
    String DLQ = "code-dlq";
    String DLQ_ROUTING_KEY = "code.dlq.routing.key";
}
