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

/**
 * 判题重试兜底任务：以 {@code question_submit.status} 为唯一权威状态源，定时把"没判完"的提交重新投递。
 *
 * <p>承担三件事：
 * <ol>
 *   <li><b>抢救卡住的 RUNNING</b>：消费者崩溃 / 复位失败 / 进程被杀时，行会永久停在"判题中"，
 *       这里超时后复位为 WAITING（原始代码中这类行会永远卡住，是已知 bug 的兜底）。</li>
 *   <li><b>重投超时的 WAITING</b>：消息丢失、judge 未启动、判题系统故障复位后，都会表现为
 *       "WAITING 且长时间未更新"，这里重新投递。投递前把 retryCount +1（计数 = 重投次数）。</li>
 *   <li><b>重试耗尽落终态</b>：retryCount 超过上限 → 置 RETRY_EXHAUSTED(4)，不再重投，
 *       等人工处理（用 SQL 查 status = 4 即可拿到全部待人工提交）。</li>
 * </ol>
 *
 * 总判题尝试次数 = 首次 1 次 + maxRetry 次重投。
 */
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

    /** 最大重试次数（重投次数上限） */
    @Value("${judge.retry.max-retry:3}")
    private int maxRetry;
    /** WAITING 超过该时长仍未判完 → 视为需要重投 */
    @Value("${judge.retry.stuck-threshold-ms:30000}")
    private long stuckThresholdMs;
    /** RUNNING 超过该时长仍未结束 → 视为卡死，复位后重投 */
    @Value("${judge.retry.running-timeout-ms:180000}")
    private long runningTimeoutMs;
    /** 每轮处理的提交数上限，避免单轮扫描过重 */
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

    /** 抢救长时间停在"判题中"的提交：复位为"待判题"，下一轮由 retryStuckWaiting 重投 */
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

    /** 重投长时间没有进展的"待判题"提交；次数耗尽则落终态 */
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
        // 先条件式递增重试计数（仍是 WAITING 才更新），成功才重投：
        // 保证"计数 == 实际重投次数"，且不会与已抢到判题权的消费者重复投递
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
