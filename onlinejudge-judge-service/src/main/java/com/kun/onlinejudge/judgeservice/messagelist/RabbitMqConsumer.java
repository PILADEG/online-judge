package com.kun.onlinejudge.judgeservice.messagelist;

import cn.hutool.json.JSONUtil;
import com.kun.onlinejudge.constant.RabbitConstant;
import com.kun.onlinejudge.judgeservice.judgement.JudgeService;
import com.kun.onlinejudge.model.entity.QuestionSubmit;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * 判题消息消费者（手动 ack）。
 *
 * <p>失败语义：判题系统故障时<b>不重投消息</b>，而是把提交在 DB 里复位为 WAITING 并记录失败原因，
 * 由 {@code JudgeRetryTask} 定时扫描后重投——重试由数据库状态机驱动，避免 requeue 热循环、
 * 以及消息在多处堆积导致重复消费；计数、上限与终态都落在 DB 上，可直接用 SQL 排查。
 *
 * <p>不可处理的消息（非法 JSON、缺字段、DB 复位失败）一律 {@code nack(requeue=false)} 转入死信队列存档，
 * 绝不丢弃、也绝不让消息悬在 unacked 状态（手动 ack 模式下，监听方法抛出异常会导致消息永远 unacked）。
 */
@Component
@Slf4j
public class RabbitMqConsumer {
    @Resource
    private JudgeService judgeService;

    @RabbitListener(queues = RabbitConstant.QUEUE)
    public void receive(String message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        boolean settled = false;
        try {
            log.info("收到判题消息：{}", message);
            QuestionSubmit questionSubmit = parse(message);
            if (questionSubmit == null || questionSubmit.getId() == null) {
                log.error("判题消息不可解析（非法 JSON 或缺 id），转入死信队列：{}", message);
                channel.basicNack(deliveryTag, false, false);
                settled = true;
                return;
            }
            try {
                judgeService.doJudge(questionSubmit);
            } catch (Exception e) {
                log.error("判题处理失败，提交 id：{}，将交由定时任务重试", questionSubmit.getId(), e);
                try {
                    judgeService.markRetryableFailure(questionSubmit, briefReason(e));
                } catch (Exception markEx) {
                    // 复位失败（如 DB 不可用）：转入死信队列存证；同时 JudgeRetryTask 会扫描"卡在 RUNNING"的提交兜底
                    log.error("复位重试状态失败，提交 id：{}，转入死信队列", questionSubmit.getId(), markEx);
                    channel.basicNack(deliveryTag, false, false);
                    settled = true;
                    return;
                }
            }
            channel.basicAck(deliveryTag, false);
            settled = true;
        } catch (Throwable t) {
            // 兜底：任何未预期异常都不能让消息悬在 unacked（重启后还会回来，形成 poison message）
            if (!settled) {
                log.error("判题消息处理出现未预期异常，转入死信队列：{}", message, t);
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }

    /** 反序列化：非法 JSON 不抛出，交由调用方走"转死信"分支 */
    private QuestionSubmit parse(String message) {
        try {
            return JSONUtil.toBean(message, QuestionSubmit.class);
        } catch (Exception e) {
            log.warn("判题消息 JSON 解析失败：{}", e.getMessage());
            return null;
        }
    }

    /** 取异常摘要作为失败原因（写入 judgeInfo，便于用开发工具排查） */
    private String briefReason(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.trim().isEmpty()) {
            msg = e.getClass().getSimpleName();
        }
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }
}
