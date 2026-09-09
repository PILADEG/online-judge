package com.kun.onlinejudge.judgeservice.judgement;

import com.kun.onlinejudge.model.entity.QuestionSubmit;

public interface JudgeService {
    public void doJudge(QuestionSubmit questionSubmit);
}
