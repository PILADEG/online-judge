package com.kun.onlinejudge.judgeservice.judgement;

import com.kun.onlinejudge.model.entity.QuestionSubmit;

public interface JudgeService {
    public void doJudge(QuestionSubmit questionSubmit);

    /**
     * 判题系统故障（可重试）：把提交复位为待判题并记录失败原因，
     * 由 JudgeRetryTask 定时重新投递；重试次数耗尽后置终态 RETRY_EXHAUSTED。
     */
    public void markRetryableFailure(QuestionSubmit questionSubmit, String reason);
}
