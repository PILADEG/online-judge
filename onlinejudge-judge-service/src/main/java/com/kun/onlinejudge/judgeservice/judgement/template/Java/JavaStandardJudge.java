package com.kun.onlinejudge.judgeservice.judgement.template.Java;

import com.kun.onlinejudge.judgeservice.judgement.template.StandardJudge;
import com.kun.onlinejudge.model.codesandbox.ExecuteResponse;
import com.kun.onlinejudge.model.judge.JudgeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JavaStandardJudge extends StandardJudge {
    @Override
    public ExecuteResponse judge(JudgeContext judgeContext) {
        return super.judge(judgeContext);
    }
}
