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
                    log.error("复位重试状态失败，提交 id：{}，转入死信队列", questionSubmit.getId(), markEx);
                    channel.basicNack(deliveryTag, false, false);
                    settled = true;
                    return;
                }
            }
            channel.basicAck(deliveryTag, false);
            settled = true;
        } catch (Throwable t) {
            if (!settled) {
                log.error("判题消息处理出现未预期异常，转入死信队列：{}", message, t);
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }

    private QuestionSubmit parse(String message) {
        try {
            return JSONUtil.toBean(message, QuestionSubmit.class);
        } catch (Exception e) {
            log.warn("判题消息 JSON 解析失败：{}", e.getMessage());
            return null;
        }
    }

    private String briefReason(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.trim().isEmpty()) {
            msg = e.getClass().getSimpleName();
        }
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }
}
