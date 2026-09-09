package com.kun.onlinejudge.judgeservice.judgement.template;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.kun.onlinejudge.model.codesandbox.ExecuteResponse;
import com.kun.onlinejudge.model.entity.Question;
import com.kun.onlinejudge.model.entity.QuestionSubmit;
import com.kun.onlinejudge.model.enums.JudgeInfoMessageEnum;
import com.kun.onlinejudge.model.enums.QuestionSubmitStatusEnum;
import com.kun.onlinejudge.model.judge.JudgeCase;
import com.kun.onlinejudge.model.judge.JudgeConfig;
import com.kun.onlinejudge.model.judge.JudgeContext;
import com.kun.onlinejudge.model.judge.JudgeInfo;
import com.kun.onlinejudge.judgeservice.utils.JudgeUtils;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Resource;

/**
 * Default Java
 */
@Slf4j
public abstract class StandardJudge implements JudgeTemplate{
    @Resource
    private JudgeUtils judgeUtils;

    protected ExecuteResponse commonJudge(JudgeContext judgeContext){
        try{
            if (judgeContext.getStatus() != null
                    && judgeContext.getStatus().equals(JudgeInfoMessageEnum.SYSTEM_ERROR.getValue())){
                judgeUtils.setSystemErrorJudge(judgeContext.getQuestion(), judgeContext.getQuestionSubmit());
                log.info("system error:{}", judgeContext.getErrorMessage());
                return ExecuteResponse.builder()
                        .status(JudgeInfoMessageEnum.SYSTEM_ERROR.getValue())
                        .errorMessage("系统错误")
                        .build();
            }
            Question question = judgeContext.getQuestion();
            QuestionSubmit questionSubmit = judgeContext.getQuestionSubmit();
            if (judgeContext.getStatus() != null
                    && judgeContext.getStatus()
                    .equals(JudgeInfoMessageEnum.COMPILE_ERROR.getValue())){
                log.info("compile error:{}", judgeContext.getErrorMessage());
                JudgeInfo judgeInfo = JudgeInfo.builder()
                        .time(0L)
                        .memory(0L)
                        .message(JudgeInfoMessageEnum.COMPILE_ERROR.getText())
                        .build();
                questionSubmit.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
                questionSubmit.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());
                judgeUtils.setJudgeResultToDatabase(question.getId(), questionSubmit, false);
                return ExecuteResponse.builder()
                        .status(JudgeInfoMessageEnum.COMPILE_ERROR.getValue())
                        .errorMessage("编译错误")
                        .build();
            }
            if (judgeContext.getStatus() != null
                    && judgeContext.getStatus().equals(JudgeInfoMessageEnum.MEMORY_LIMIT_EXCEEDED.getValue())){
                JudgeInfo judgeInfo = JudgeInfo.builder()
                        .time(judgeContext.getTime())
                        .memory(judgeContext.getMemory())
                        .message(JudgeInfoMessageEnum.MEMORY_LIMIT_EXCEEDED.getText())
                        .build();
                questionSubmit.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
                questionSubmit.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());
                judgeUtils.setJudgeResultToDatabase(question.getId(), questionSubmit, false);
                return ExecuteResponse.builder()
                        .status(JudgeInfoMessageEnum.MEMORY_LIMIT_EXCEEDED.getValue())
                        .errorMessage("内存超限")
                        .build();
            }
            if (judgeContext.getStatus() != null
                    && judgeContext.getStatus().equals(JudgeInfoMessageEnum.TIME_LIMIT_EXCEEDED.getValue())){
                JudgeInfo judgeInfo = JudgeInfo.builder()
                        .time(judgeContext.getTime())
                        .memory(judgeContext.getMemory())
                        .message(JudgeInfoMessageEnum.TIME_LIMIT_EXCEEDED.getText())
                        .build();
                questionSubmit.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
                questionSubmit.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());
                judgeUtils.setJudgeResultToDatabase(question.getId(), questionSubmit, false);
                return ExecuteResponse.builder()
                        .status(JudgeInfoMessageEnum.TIME_LIMIT_EXCEEDED.getValue())
                        .errorMessage("时间超限")
                        .build();
            }
            if (judgeContext.getStatus() != null
                    && judgeContext.getStatus().equals(JudgeInfoMessageEnum.RUNTIME_ERROR.getValue())){
                String errorMessage = StrUtil.isNotBlank(judgeContext.getErrorMessage()) ? judgeContext.getErrorMessage() : "";
                JudgeInfo judgeInfo = JudgeInfo.builder()
                        .time(judgeContext.getTime())
                        .memory(judgeContext.getMemory())
                        .message(JudgeInfoMessageEnum.RUNTIME_ERROR.getText() + errorMessage)
                        .build();
                questionSubmit.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
                questionSubmit.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());
                judgeUtils.setJudgeResultToDatabase(question.getId(), questionSubmit, false);
                return ExecuteResponse.builder()
                        .status(JudgeInfoMessageEnum.RUNTIME_ERROR.getValue())
                        .errorMessage("运行时错误"+errorMessage)
                        .build();
            }
            return null;
        } catch (Exception e) {
            log.error("commonJudge error", e);
            throw new RuntimeException(e);
        }
    }
    protected ExecuteResponse judgeAnswer(JudgeContext judgeContext){
        Question question = judgeContext.getQuestion();
        QuestionSubmit questionSubmit = judgeContext.getQuestionSubmit();
        List<String> outputList = judgeContext.getOutputList();
        List<JudgeCase> judgeCaseList = JSONUtil.toList(question.getJudgeCases(), JudgeCase.class);
        List<String> expectedList = judgeCaseList.stream()
                .map(JudgeCase::getOutputCase).collect(Collectors.toList());
        log.info("judgeAnswer start:{}",outputList);
        log.info("expectedAnswer start:{}",expectedList);
        if (outputList == null){
            JudgeInfo judgeInfo = JudgeInfo.builder()
                    .time(judgeContext.getTime())
                    .memory(judgeContext.getMemory())
                    .message(JudgeInfoMessageEnum.WRONG_ANSWER.getText())
                    .build();
            questionSubmit.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());
            questionSubmit.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
            judgeUtils.setJudgeResultToDatabase(question.getId(), questionSubmit, false);
            return ExecuteResponse.builder()
                    .status(JudgeInfoMessageEnum.WRONG_ANSWER.getValue())
                    .time(judgeContext.getTime())
                    .memory(judgeContext.getMemory())
                    .errorMessage("答案错误")
                    .build();
        }
        if (outputList.size() != expectedList.size()){
            JudgeInfo judgeInfo = JudgeInfo.builder()
                    .time(judgeContext.getTime())
                    .memory(judgeContext.getMemory())
                    .message(JudgeInfoMessageEnum.WRONG_ANSWER.getText())
                    .build();
            questionSubmit.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());
            questionSubmit.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
            judgeUtils.setJudgeResultToDatabase(question.getId(), questionSubmit, false);
            return ExecuteResponse.builder()
                    .status(JudgeInfoMessageEnum.WRONG_ANSWER.getValue())
                    .time(judgeContext.getTime())
                    .memory(judgeContext.getMemory())
                    .errorMessage("答案错误")
                    .build();
        }
        for (int i = 0; i < outputList.size(); i++) {
            log.info("judgeAnswer:{}",outputList.get(i));
            log.info("expectedAnswer:{}",expectedList.get(i));
            String output = outputList.get(i).trim();
            if (!output.equals(expectedList.get(i))){
                JudgeInfo judgeInfo = JudgeInfo.builder()
                        .time(judgeContext.getTime())
                        .memory(judgeContext.getMemory())
                        .message(JudgeInfoMessageEnum.WRONG_ANSWER.getText())
                        .build();
                questionSubmit.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());
                questionSubmit.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
                judgeUtils.setJudgeResultToDatabase(question.getId(), questionSubmit, false);
                return ExecuteResponse.builder()
                        .status(JudgeInfoMessageEnum.WRONG_ANSWER.getValue())
                        .time(judgeContext.getTime())
                        .memory(judgeContext.getMemory())
                        .errorMessage("答案错误")
                        .build();
            }
        }
        JudgeInfo judgeInfo = JudgeInfo.builder()
                .time(judgeContext.getTime())
                .memory(judgeContext.getMemory())
                .message(JudgeInfoMessageEnum.ACCEPTED.getText())
                .build();
        questionSubmit.setStatus(QuestionSubmitStatusEnum.SUCCEED.getValue());
        questionSubmit.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
        judgeUtils.setJudgeResultToDatabase(question.getId(), questionSubmit, true);
        return ExecuteResponse.builder()
                .status(JudgeInfoMessageEnum.ACCEPTED.getValue())
                .message(JudgeInfoMessageEnum.ACCEPTED.getText())
                .time(judgeContext.getTime())
                .memory(judgeContext.getMemory())
                .build();
    }
    @Override
    public ExecuteResponse judge(JudgeContext judgeContext){
        log.info("judge start");
        ExecuteResponse commonResponse = commonJudge(judgeContext);
        if (commonResponse != null){
            return commonResponse;
        }
        ExecuteResponse answerResponse = judgeAnswer(judgeContext);
        return answerResponse;
    }

}
