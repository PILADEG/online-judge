package com.kun.onlinejudge.model.dto.question;

import com.kun.onlinejudge.model.judge.JudgeCase;
import com.kun.onlinejudge.model.judge.JudgeConfig;
import java.io.Serializable;
import java.util.List;
import lombok.Data;

@Data
public class QuestionUpdateRequest implements Serializable {

    private Long id;

    private String title;

    private String content;

    private List<String> tags;

    private String answer;

    private List<JudgeCase> judgeCases;

    private JudgeConfig judgeConfig;

    private static final long serialVersionUID = 1L;
}
