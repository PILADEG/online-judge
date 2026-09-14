package com.kun.onlinejudge.judgeservice.messagelist;

import com.kun.onlinejudge.constant.RabbitConstant;
import com.kun.onlinejudge.judgeservice.mapper.DlqMessageMapper;
import com.kun.onlinejudge.model.entity.DlqMessage;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 死信队列消费者：只做"落库存档"，不做任何重试/重投。
 *
 * <p>用途：把判题消费者拒收的消息（解析失败、复位状态失败等）留痕，便于用 SQL 排查。
 * 人工恢复的正确入口是重置数据库状态（status=0, retryCount=0）后由 JudgeRetryTask 重投，
 * 而不是从这里把消息 requeue 回主队列（doJudge 的乐观锁只接受 WAITING，重放会被跳过）。
 */
@Component
@Slf4j
public class DlqMessageConsumer {

    @Resource
    private DlqMessageMapper dlqMessageMapper;

    @RabbitListener(queues = RabbitConstant.DLQ)
    public void receive(Message message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String deathReason = resolveDeathReason(message);
        Integer deathCount = resolveDeathCount(message);
        // 先无条件把原文打进日志：即使随后落库失败，也有痕迹可查
        log.error("收到死信消息：reason={}, count={}, messageId={}, body={}",
                deathReason, deathCount, message.getMessageProperties().getMessageId(), body);
        try {
            DlqMessage record = new DlqMessage();
            record.setSourceQueue(RabbitConstant.QUEUE);
            record.setMessageId(message.getMessageProperties().getMessageId());
            record.setBody(body);
            record.setDeathReason(deathReason);
            record.setDeathCount(deathCount);
            dlqMessageMapper.insert(record);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 落库失败（如 DB 不可用）：重新入队等待下次尝试。死信队列本身不配 DLX，
            // 且预期流量极低，不会造成热循环；原文已在上面的 ERROR 日志中留痕。
            log.error("死信消息落库失败，消息重新入队等待重试", e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    /** 从 x-death 头取死信原因（broker 自动附加）：rejected / expired / maxlen */
    private String resolveDeathReason(Message message) {
        Map<String, Object> first = firstDeath(message);
        if (first == null || first.get("reason") == null) {
            return null;
        }
        return String.valueOf(first.get("reason"));
    }

    /** 从 x-death 头取被死信化的次数 */
    private Integer resolveDeathCount(Message message) {
        Map<String, Object> first = firstDeath(message);
        if (first == null || first.get("count") == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(first.get("count")));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstDeath(Message message) {
        Object xDeath = message.getMessageProperties().getHeaders().get("x-death");
        if (!(xDeath instanceof List)) {
            return null;
        }
        List<Object> deathList = (List<Object>) xDeath;
        if (deathList.isEmpty() || !(deathList.get(0) instanceof Map)) {
            return null;
        }
        return (Map<String, Object>) deathList.get(0);
    }
}
