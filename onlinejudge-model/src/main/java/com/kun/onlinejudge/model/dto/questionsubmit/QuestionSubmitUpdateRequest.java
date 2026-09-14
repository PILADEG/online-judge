package com.kun.onlinejudge.model.dto.questionsubmit;

import com.kun.onlinejudge.model.judge.JudgeInfo;
import java.io.Serializable;
import lombok.Data;

/**
 * 更新题目提交请求
 */
@Data
public class QuestionSubmitUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 语言
     */
    private String language;

    /**
     * 提交代码
     */
    private String code;

    /**
     * 判题信息
     */
    private JudgeInfo judgeInfo;

    /**
     * 状态（0-待判题 1-判题中 2-成功 3-失败 4-系统故障重试耗尽待人工处理）
     */
    private Integer status;

    /**
     * 题目 id
     */
    private Long questionId;

    private static final long serialVersionUID = 1L;
}
