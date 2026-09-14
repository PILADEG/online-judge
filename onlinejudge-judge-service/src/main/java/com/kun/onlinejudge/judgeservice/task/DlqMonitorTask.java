package com.kun.onlinejudge.judgeservice.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kun.onlinejudge.constant.RabbitConstant;
import com.kun.onlinejudge.judgeservice.mapper.DlqMessageMapper;
import com.kun.onlinejudge.model.entity.DlqMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.Properties;

/**
 * 死信告警：两个互补的信号。
 *
 * <ol>
 *   <li><b>最近有死信入库</b>（查 dlq_message 表）——这是真正需要人工关注的信号：
 *       死信消费者会立即把消息落库并 ack，所以队列深度通常为 0，只盯队列深度会漏报。</li>
 *   <li><b>死信队列积压</b>（RabbitAdmin 查队列深度）——反向兜底：
 *       如果死信消费者本身挂了/落库一直失败，消息会堆在队列里，这时靠它发现。</li>
 * </ol>
 */
@Component
@Slf4j
public class DlqMonitorTask {

    @Resource
    private RabbitAdmin rabbitAdmin;
    @Resource
    private DlqMessageMapper dlqMessageMapper;

    /** 队列积压达到该条数即告警 */
    @Value("${judge.dlq.alert-threshold:1}")
    private int alertThreshold;
    /** 死信入库的观察窗口（分钟内有过入库就告警） */
    @Value("${judge.dlq.recent-window-minutes:10}")
    private int recentWindowMinutes;

    @Scheduled(fixedDelayString = "${judge.dlq.alert-interval-ms:60000}")
    public void checkDlq() {
        checkRecentDlqMessages();
        checkDlqDepth();
    }

    /** 信号一：最近窗口内有新的死信入库 */
    private void checkRecentDlqMessages() {
        try {
            Date since = new Date(System.currentTimeMillis() - recentWindowMinutes * 60_000L);
            Long recent = dlqMessageMapper.selectCount(new QueryWrapper<DlqMessage>().ge("createTime", since));
            if (recent != null && recent > 0) {
                log.error("【告警】最近 {} 分钟内有 {} 条死信入库，需人工排查（SQL：select * from dlq_message order by createTime desc）",
                        recentWindowMinutes, recent);
            }
        } catch (Exception e) {
            log.error("死信入库检查失败", e);
        }
    }

    /** 信号二：死信队列本身积压（说明死信消费者可能没在消费） */
    private void checkDlqDepth() {
        try {
            Properties properties = rabbitAdmin.getQueueProperties(RabbitConstant.DLQ);
            if (properties == null) {
                log.warn("死信队列 {} 尚不存在（服务启动声明后即可见）", RabbitConstant.DLQ);
                return;
            }
            Object raw = properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
            int messageCount = raw == null ? 0 : Integer.parseInt(String.valueOf(raw));
            if (messageCount >= alertThreshold) {
                log.error("【告警】死信队列 {} 积压 {} 条（阈值 {}）——死信消费者可能未正常工作",
                        RabbitConstant.DLQ, messageCount, alertThreshold);
            }
        } catch (Exception e) {
            log.error("死信队列深度检查失败", e);
        }
    }
}
