package com.kun.onlinejudge.judgeservice.judgement.template;

import com.kun.onlinejudge.model.codesandbox.ExecuteResponse;
import com.kun.onlinejudge.model.judge.JudgeContext;

public interface JudgeTemplate {
    public ExecuteResponse judge(JudgeContext judgeContext);
}
