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

@Component
@Slf4j
public class RabbitMqConsumer {
    @Resource
    private JudgeService judgeService;

    @RabbitListener(queues = RabbitConstant.QUEUE)
    public void receive(String message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("收到判题消息：{}", message);
        QuestionSubmit questionSubmit = JSONUtil.toBean(message, QuestionSubmit.class);
        if (questionSubmit == null || questionSubmit.getId() == null) {
            log.error("判题消息解析失败：{}", message);
            // 解析失败直接 ack，避免无限重投
            channel.basicAck(deliveryTag, false);
            return;
        }
        try {
            judgeService.doJudge(questionSubmit);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("判题处理失败，提交 id：{}", questionSubmit.getId(), e);
            // 不重投，避免 poison message 无限循环
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
