package com.kun.onlinejudge.model.judge;

import com.kun.onlinejudge.model.entity.Question;
import com.kun.onlinejudge.model.entity.QuestionSubmit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JudgeContext implements Serializable {
    private String status;

    private String message;

    private String errorMessage;

    private Long time;

    private Long memory;

    private List<String> outputList;

    private String language;

    private Question question;

    private QuestionSubmit questionSubmit;

    private static final long serialVersionUID = 1L;
}
