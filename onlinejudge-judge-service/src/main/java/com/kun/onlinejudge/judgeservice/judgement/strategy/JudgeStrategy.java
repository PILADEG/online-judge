package com.kun.onlinejudge.judgeservice.judgement.strategy;

import com.kun.onlinejudge.model.codesandbox.ExecuteResponse;
import com.kun.onlinejudge.model.judge.JudgeContext;

public interface JudgeStrategy {
    public ExecuteResponse judge(JudgeContext judgeContext);
}
