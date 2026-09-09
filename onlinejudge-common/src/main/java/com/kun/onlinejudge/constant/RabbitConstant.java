package com.kun.onlinejudge.constant;

/**
 * RabbitMQ 拓扑常量（submit producer 与 judge consumer 同源）
 */
public interface RabbitConstant {

    String EXCHANGE = "code-exchange";
    String QUEUE = "code-queue";
    String ROUTING_KEY = "code.routing.key";
}
