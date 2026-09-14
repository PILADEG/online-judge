package com.kun.onlinejudge.constant;

/**
 * RabbitMQ 拓扑常量（submit producer 与 judge consumer 同源）
 */
public interface RabbitConstant {

    String EXCHANGE = "code-exchange";
    String QUEUE = "code-queue";
    String ROUTING_KEY = "code.routing.key";

    /**
     * 死信交换机：主队列中 basicNack(requeue=false) 的消息会经它转入死信队列。
     * 注意：只做"收容存档"，不承担重试职责——判题重试由 status 状态机 + JudgeRetryTask 驱动。
     */
    String DLX_EXCHANGE = "code-dlx-exchange";
    /** 死信队列：只收不自动消费（由埋点消费者落库存档），且不配置自己的 DLX（否则死循环） */
    String DLQ = "code-dlq";
    String DLQ_ROUTING_KEY = "code.dlq.routing.key";
}
