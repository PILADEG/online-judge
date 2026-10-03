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
            log.error("死信消息落库失败，消息重新入队等待重试", e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private String resolveDeathReason(Message message) {
        Map<String, Object> first = firstDeath(message);
        if (first == null || first.get("reason") == null) {
            return null;
        }
        return String.valueOf(first.get("reason"));
    }

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
