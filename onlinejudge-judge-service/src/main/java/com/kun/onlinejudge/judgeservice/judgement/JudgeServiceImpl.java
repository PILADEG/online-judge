package com.kun.onlinejudge.judgeservice.judgement;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.kun.onlinejudge.judgeservice.codesandbox.CodeSandBox;
import com.kun.onlinejudge.judgeservice.codesandbox.CodeSandBoxFactory;
import com.kun.onlinejudge.judgeservice.codesandbox.CodeSandBoxProxy;
import com.kun.onlinejudge.judgeservice.judgement.factory.JudgeStrategyFactory;
import com.kun.onlinejudge.judgeservice.judgement.strategy.JudgeStrategy;
import com.kun.onlinejudge.judgeservice.mapper.QuestionMapper;
import com.kun.onlinejudge.judgeservice.mapper.QuestionSubmitMapper;
import com.kun.onlinejudge.model.codesandbox.ExecuteMessage;
import com.kun.onlinejudge.model.entity.Question;
import com.kun.onlinejudge.model.entity.QuestionSubmit;
import com.kun.onlinejudge.model.enums.JudgeInfoMessageEnum;
import com.kun.onlinejudge.model.enums.QuestionSubmitStatusEnum;
import com.kun.onlinejudge.model.judge.JudgeCase;
import com.kun.onlinejudge.model.judge.JudgeConfig;
import com.kun.onlinejudge.model.judge.JudgeContext;
import com.kun.onlinejudge.judgeservice.utils.JudgeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.kun.onlinejudge.model.codesandbox.ExecuteResponse;
import javax.annotation.Resource;

@Service
@Slf4j
public class JudgeServiceImpl implements JudgeService {
    @Value("${codesandbox.type}")
    private String type;
    @Resource
    private QuestionMapper questionMapper;
    @Resource
    private QuestionSubmitMapper questionSubmitMapper;
    @Resource
    private CodeSandBoxFactory codeSandBoxFactory;
    @Resource
    private JudgeStrategyFactory judgeStrategyFactory;
    @Resource
    private JudgeUtils judgeUtils;
    @Override
    public void doJudge(QuestionSubmit questionSubmit) {
        // 乐观锁抢占判题权：只有待判题(WAITING)的提交能被原子地改为判题中(RUNNING)
        boolean acquired = questionSubmitMapper.update(null, new UpdateWrapper<QuestionSubmit>()
                .eq("id", questionSubmit.getId())
                .eq("status", QuestionSubmitStatusEnum.WAITING.getValue())
                .set("status", QuestionSubmitStatusEnum.RUNNING.getValue())) == 1;
        if (!acquired) {
            log.info("提交 {} 正在判题中或已结束，跳过重复判题", questionSubmit.getId());
            return;
        }
        Question question = questionMapper.selectById(questionSubmit.getQuestionId());
        if (question == null) {
            // 题目不存在属不可重试的系统故障（重试也不会出现）→ 直接置终态待人工
            judgeUtils.setAbortJudge(null, questionSubmit,
                    "题目不存在, questionId=" + questionSubmit.getQuestionId());
            return;
        }
        ExecuteMessage executeMessage = ExecuteMessage.builder()
                .judgeCases(JSONUtil.toList(question.getJudgeCases(), JudgeCase.class))
                .code(questionSubmit.getCode())
                .language(questionSubmit.getLanguage())
                .judgeConfig(JSONUtil.toBean(question.getJudgeConfig(), JudgeConfig.class))
                .build();
        CodeSandBox instance = new CodeSandBoxProxy(codeSandBoxFactory.newInstance(type));
        ExecuteResponse response = instance.doExecute(executeMessage);
        JudgeContext judgeContext = JudgeContext.builder()
                .question(question)
                .memory(response.getMemory())
                .message(StrUtil.isNotBlank(response.getMessage())?response.getMessage():null)
                .errorMessage(StrUtil.isNotBlank(response.getErrorMessage())?response.getErrorMessage():null)
                .time(response.getTime())
                .outputList(response.getOutputList())
                .status(response.getStatus())
                .language(questionSubmit.getLanguage())
                .questionSubmit(questionSubmit)
                .build();
        JudgeStrategy judgeStrategy = judgeStrategyFactory.getJudgeStrategy(questionSubmit.getLanguage());
        if (judgeStrategy == null){
            // 判题语言不支持属不可重试故障（重试也不会支持）→ 直接置终态待人工
            judgeUtils.setAbortJudge(question, questionSubmit,
                    "不支持的判题语言: " + questionSubmit.getLanguage());
            return;
        }
        ExecuteResponse finalResponse=null;
        try{
             finalResponse = judgeStrategy.judge(judgeContext);
             log.info("judge result:{}", finalResponse);
        } catch (RetryableJudgeException e) {
            // 可重试系统故障（沙箱不可用等）：保持异常类型，交给消费端复位 + 定时重投
            throw e;
        } catch (Exception e) {
            log.error("judge error", e);
            throw new RetryableJudgeException("判题执行异常：" + e.getMessage(), e);
        }
        if (finalResponse == null){
            // 模板未给出结果（题目参数/数据异常）属不可重试故障 → 直接置终态待人工
            judgeUtils.setAbortJudge(question, questionSubmit,
                    "判题无返回结果（题目参数或数据异常）");
        }

    }

    @Override
    public void markRetryableFailure(QuestionSubmit questionSubmit, String reason) {
        judgeUtils.markRetryableFailure(questionSubmit, reason);
    }
}
