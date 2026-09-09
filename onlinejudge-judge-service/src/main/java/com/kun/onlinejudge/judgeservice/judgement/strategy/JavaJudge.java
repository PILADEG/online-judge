package com.kun.onlinejudge.judgeservice.judgement.strategy;

import cn.hutool.json.JSONUtil;
import com.kun.onlinejudge.judgeservice.judgement.template.Java.JavaStandardJudge;
import com.kun.onlinejudge.model.codesandbox.ExecuteResponse;
import com.kun.onlinejudge.model.entity.Question;
import com.kun.onlinejudge.model.enums.JudgeTypeEnum;
import com.kun.onlinejudge.model.judge.JudgeConfig;
import com.kun.onlinejudge.model.judge.JudgeContext;
import javax.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class JavaJudge implements JudgeStrategy{

    @Resource
    private JavaStandardJudge javaStandardJudge;

    @Override
    public ExecuteResponse judge(JudgeContext judgeContext) {
        if (judgeContext == null
         || judgeContext.getQuestion() == null
         || judgeContext.getQuestionSubmit() == null){
            return null;
        }
        Question question = judgeContext.getQuestion();
        JudgeConfig judgeConfig = JSONUtil.toBean(question.getJudgeConfig(), JudgeConfig.class);

        if (judgeConfig == null){
            return null;
        }
        Integer judgeType = judgeConfig.getJudgeType() == null ? 0 : judgeConfig.getJudgeType();
        if (judgeType.equals(JudgeTypeEnum.STANDARD.getValue())){
            return javaStandardJudge.judge(judgeContext);
        }
        return null;
    }
}
