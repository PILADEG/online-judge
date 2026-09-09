package com.kun.onlinejudge.judgeservice.judgement.factory;

import com.kun.onlinejudge.judgeservice.judgement.strategy.JavaJudge;
import com.kun.onlinejudge.judgeservice.judgement.strategy.JudgeStrategy;
import javax.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class JudgeStrategyFactory {

    private static final String JAVA = "java";

    @Resource
    private JavaJudge javaJudge;

    public JudgeStrategy getJudgeStrategy(String language) {
        if (JAVA.equals(language)) {
            return javaJudge;
        }
        return null;
    }
}
