package com.kun.onlinejudge.model.dto.question;

import com.kun.onlinejudge.model.judge.JudgeCase;
import com.kun.onlinejudge.model.judge.JudgeConfig;
import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 创建题目请求
 */
@Data
public class QuestionAddRequest implements Serializable {

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 题目答案
     */
    private String answer;

    /**
     * 判题用例
     */
    private List<JudgeCase> judgeCases;

    /**
     * 判题配置
     */
    private JudgeConfig judgeConfig;

    private static final long serialVersionUID = 1L;
}
