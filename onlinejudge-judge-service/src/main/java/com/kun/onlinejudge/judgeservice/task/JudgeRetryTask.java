package com.kun.onlinejudge.judgeservice.task;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.kun.onlinejudge.constant.RabbitConstant;
import com.kun.onlinejudge.judgeservice.mapper.QuestionMapper;
import com.kun.onlinejudge.judgeservice.mapper.QuestionSubmitMapper;
import com.kun.onlinejudge.judgeservice.utils.JudgeUtils;
import com.kun.onlinejudge.model.entity.Question;
import com.kun.onlinejudge.model.entity.QuestionSubmit;
import com.kun.onlinejudge.model.enums.QuestionSubmitStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Component
@Slf4j
public class JudgeRetryTask {

    @Resource
    private QuestionSubmitMapper questionSubmitMapper;
    @Resource
    private QuestionMapper questionMapper;
    @Resource
    private JudgeUtils judgeUtils;
    @Resource
    private RabbitTemplate rabbitTemplate;

    @Value("${judge.retry.max-retry:3}")
    private int maxRetry;
    @Value("${judge.retry.stuck-threshold-ms:30000}")
    private long stuckThresholdMs;
    @Value("${judge.retry.running-timeout-ms:180000}")
    private long runningTimeoutMs;
    @Value("${judge.retry.batch-size:20}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${judge.retry.scan-interval-ms:30000}")
    public void scanAndRetry() {
        try {
            recoverStuckRunning();
            retryStuckWaiting();
        } catch (Exception e) {
            log.error("判题重试任务执行异常", e);
        }
    }

    private void recoverStuckRunning() {
        Date before = new Date(System.currentTimeMillis() - runningTimeoutMs);
        List<QuestionSubmit> stuckList = questionSubmitMapper.selectList(new QueryWrapper<QuestionSubmit>()
                .eq("status", QuestionSubmitStatusEnum.RUNNING.getValue())
                .lt("updateTime", before)
                .orderByAsc("updateTime")
                .last("limit " + batchSize));
        for (QuestionSubmit submit : stuckList) {
            int updated = questionSubmitMapper.update(null, new UpdateWrapper<QuestionSubmit>()
                    .eq("id", submit.getId())
                    .eq("status", QuestionSubmitStatusEnum.RUNNING.getValue())
                    .set("status", QuestionSubmitStatusEnum.WAITING.getValue()));
            if (updated == 1) {
                log.warn("提交 {} 长时间处于判题中（可能消费者中断），已复位为待判题等待重投", submit.getId());
            }
        }
    }

    private void retryStuckWaiting() {
        Date before = new Date(System.currentTimeMillis() - stuckThresholdMs);
        List<QuestionSubmit> stuckList = questionSubmitMapper.selectList(new QueryWrapper<QuestionSubmit>()
                .eq("status", QuestionSubmitStatusEnum.WAITING.getValue())
                .lt("updateTime", before)
                .orderByAsc("updateTime")
                .last("limit " + batchSize));
        for (QuestionSubmit submit : stuckList) {
            retryOrAbort(submit);
        }
    }

    private void retryOrAbort(QuestionSubmit submit) {
        int nextRetry = (submit.getRetryCount() == null ? 0 : submit.getRetryCount()) + 1;
        if (nextRetry > maxRetry) {
            Question question = questionMapper.selectById(submit.getQuestionId());
            boolean aborted = judgeUtils.tryAbortRetryExhausted(question, submit.getId(),
                    "判题系统故障重试 " + maxRetry + " 次仍失败；最后一次 judgeInfo=" + submit.getJudgeInfo());
            if (aborted) {
                log.error("提交 {} 判题重试耗尽，已置终态待人工处理（SQL：WHERE status = {})",
                        submit.getId(), QuestionSubmitStatusEnum.RETRY_EXHAUSTED.getValue());
            }
            return;
        }
        int updated = questionSubmitMapper.update(null, new UpdateWrapper<QuestionSubmit>()
                .eq("id", submit.getId())
                .eq("status", QuestionSubmitStatusEnum.WAITING.getValue())
                .set("retryCount", nextRetry));
        if (updated != 1) {
            return;
        }
        submit.setRetryCount(nextRetry);
        rabbitTemplate.convertAndSend(RabbitConstant.EXCHANGE, RabbitConstant.ROUTING_KEY,
                JSONUtil.toJsonStr(submit));
        log.warn("提交 {} 判题重投（第 {} / {} 次）", submit.getId(), nextRetry, maxRetry);
    }
}
