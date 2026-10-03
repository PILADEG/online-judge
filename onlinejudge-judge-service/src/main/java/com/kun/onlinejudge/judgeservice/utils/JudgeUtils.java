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

@Component
@Slf4j
public class JudgeUtils {
    @Resource
    private QuestionMapper questionMapper;
    @Resource
    private QuestionSubmitMapper questionSubmitMapper;

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

    @Transactional(rollbackFor = Exception.class)
    public void setAbortJudge(Question question, QuestionSubmit questionSubmit, String reason) {
        increaseSubmitNum(question);
        questionSubmit.setStatus(QuestionSubmitStatusEnum.RETRY_EXHAUSTED.getValue());
        questionSubmit.setJudgeInfo(buildJudgeInfoJson(reason));
        questionSubmitMapper.updateById(questionSubmit);
        log.error("提交 {} 判题中断，置终态待人工处理；原因：{}", questionSubmit.getId(), reason);
    }

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
