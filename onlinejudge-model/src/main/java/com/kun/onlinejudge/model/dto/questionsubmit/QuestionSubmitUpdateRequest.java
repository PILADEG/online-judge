package com.kun.onlinejudge.model.dto.questionsubmit;

import com.kun.onlinejudge.model.judge.JudgeInfo;
import java.io.Serializable;
import lombok.Data;

@Data
public class QuestionSubmitUpdateRequest implements Serializable {

    private Long id;

    private String language;

    private String code;

    private JudgeInfo judgeInfo;

    private Integer status;

    private Long questionId;

    private static final long serialVersionUID = 1L;
}
