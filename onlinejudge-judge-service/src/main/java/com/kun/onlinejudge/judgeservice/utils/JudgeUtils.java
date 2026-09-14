package com.kun.onlinejudge.judgeservice.utils;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.kun.onlinejudge.judgeservice.mapper.QuestionMapper;
import com.kun.onlinejudge.judgeservice.mapper.QuestionSubmitMapper;
import com.kun.onlinejudge.model.entity.Question;
import com.kun.onlinejudge.model.entity.QuestionSubmit;
import com.kun.onlinejudge.model.enums.QuestionSubmitStatusEnum;
import com.kun.onlinejudge.model.judge.JudgeInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * 判题结果回写。
 *
 * <p>三类写入路径：
 * <ul>
 *   <li>{@link #setJudgeResultToDatabase}：判题得出了结果（通过/未通过）→ status 由调用方设定；</li>
 *   <li>{@link #markRetryableFailure}：系统故障 → 把 RUNNING 复位为 WAITING 并记录原因，等定时任务重投；</li>
 *   <li>{@link #setAbortJudge} / {@link #tryAbortRetryExhausted}：不可重试或重试耗尽 → 落终态 RETRY_EXHAUSTED。</li>
 * </ul>
 */
@Component
@Slf4j
public class JudgeUtils {
    @Resource
    private QuestionMapper questionMapper;
    @Resource
    private QuestionSubmitMapper questionSubmitMapper;

    /**
     * 判题系统故障：把"判题中"复位为"待判题"并记录最后一次失败原因（写入 judgeInfo）。
     * 重试次数由 JudgeRetryTask 在重新投递前 +1。仅当行仍为 RUNNING 时复位，
     * 避免覆盖已经写好的终态结果（例如模板已落 FAILED 后才抛出的异常）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markRetryableFailure(QuestionSubmit questionSubmit, String reason) {
        int updated = questionSubmitMapper.update(null, new UpdateWrapper<QuestionSubmit>()
                .eq("id", questionSubmit.getId())
                .eq("status", QuestionSubmitStatusEnum.RUNNING.getValue())
                .set("status", QuestionSubmitStatusEnum.WAITING.getValue())
                .set("judgeInfo", buildJudgeInfoJson(reason)));
        if (updated == 1) {
            log.warn("提交 {} 判题系统故障，已复位为待判题等待重试；原因：{}", questionSubmit.getId(), reason);
        } else {
            log.info("提交 {} 复位跳过（状态已非判题中，可能已写入结果）", questionSubmit.getId());
        }
    }

    /**
     * 判题中断终态：不可重试的系统故障（题目不存在、判题语言不支持等）。
     * 调用时本线程持有该行（RUNNING），直接无条件落终态 RETRY_EXHAUSTED。
     * question 可为 null（题目已不存在时不增加提交计数）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void setAbortJudge(Question question, QuestionSubmit questionSubmit, String reason) {
        increaseSubmitNum(question);
        questionSubmit.setStatus(QuestionSubmitStatusEnum.RETRY_EXHAUSTED.getValue());
        questionSubmit.setJudgeInfo(buildJudgeInfoJson(reason));
        questionSubmitMapper.updateById(questionSubmit);
        log.error("提交 {} 判题中断，置终态待人工处理；原因：{}", questionSubmit.getId(), reason);
    }

    /**
     * 重试次数耗尽 → 条件式落终态（仅当行仍为 WAITING，避免与重投后被消费的请求竞争）。
     *
     * @return 是否成功置为终态（false 表示行已被其它路径处理）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean tryAbortRetryExhausted(Question question, Long questionSubmitId, String reason) {
        int updated = questionSubmitMapper.update(null, new UpdateWrapper<QuestionSubmit>()
                .eq("id", questionSubmitId)
                .eq("status", QuestionSubmitStatusEnum.WAITING.getValue())
                .set("status", QuestionSubmitStatusEnum.RETRY_EXHAUSTED.getValue())
                .set("judgeInfo", buildJudgeInfoJson(reason)));
        if (updated != 1) {
            return false;
        }
        increaseSubmitNum(question);
        log.error("提交 {} 判题重试耗尽，置终态待人工处理；原因：{}", questionSubmitId, reason);
        return true;
    }

    /**
     * 判题得出结果（通过/未通过）时回写：提交计数 +1（通过则另加通过数），并更新提交行。
     * status / judgeInfo 由调用方（判题模板）先行设置。
     */
    @Transactional(rollbackFor = Exception.class)
    public void setJudgeResultToDatabase(Long questionId,
                                         QuestionSubmit questionSubmit,
                                         Boolean isAccepted) {
        if (isAccepted) {
            questionMapper.update(null, new UpdateWrapper<Question>()
                    .setSql("acceptedNum = acceptedNum + 1")
                    .setSql("submitNum = submitNum + 1")
                    .eq("id", questionId));
        } else {
            questionMapper.update(null, new UpdateWrapper<Question>()
                    .setSql("submitNum = submitNum + 1")
                    .eq("id", questionId));
        }
        questionSubmitMapper.updateById(questionSubmit);
    }

    private void increaseSubmitNum(Question question) {
        if (question == null) {
            return;
        }
        questionMapper.update(null, new UpdateWrapper<Question>()
                .setSql("submitNum = submitNum + 1")
                .eq("id", question.getId()));
    }

    private String buildJudgeInfoJson(String reason) {
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage(reason);
        judgeInfo.setMemory(0L);
        judgeInfo.setTime(0L);
        return JSONUtil.toJsonStr(judgeInfo);
    }
}
