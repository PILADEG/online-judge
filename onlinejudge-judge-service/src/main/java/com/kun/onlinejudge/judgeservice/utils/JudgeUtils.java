package com.kun.onlinejudge.judgeservice.utils;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.kun.onlinejudge.judgeservice.mapper.QuestionMapper;
import com.kun.onlinejudge.judgeservice.mapper.QuestionSubmitMapper;
import com.kun.onlinejudge.model.entity.Question;
import com.kun.onlinejudge.model.entity.QuestionSubmit;
import com.kun.onlinejudge.model.enums.JudgeInfoMessageEnum;
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
    public void setSystemErrorJudge(Question question, QuestionSubmit questionSubmit){
        questionMapper.update(null, new UpdateWrapper<Question>()
                .setSql("submitNum = submitNum + 1")
                .eq("id", question.getId()));
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage(JudgeInfoMessageEnum.SYSTEM_ERROR.getText());
        judgeInfo.setMemory(0L);
        judgeInfo.setTime(0L);
        questionSubmit.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());
        questionSubmit.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
        questionSubmitMapper.updateById(questionSubmit);
    }
    @Transactional(rollbackFor = Exception.class)
    public void setJudgeResultToDatabase(Long questionId,
                                         QuestionSubmit questionSubmit,
                                         Boolean isAccepted){
        if (isAccepted){
            questionMapper.update(null, new UpdateWrapper<Question>()
                    .setSql("acceptedNum = acceptedNum + 1")
                    .setSql("submitNum = submitNum + 1")
                    .eq("id", questionId));
        }else{
            questionMapper.update(null, new UpdateWrapper<Question>()
                    .setSql("submitNum = submitNum + 1")
                    .eq("id", questionId));
        }
        questionSubmitMapper.updateById(questionSubmit);
    }
}
