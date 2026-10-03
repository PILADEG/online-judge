package com.kun.onlinejudge.model.dto.questionsubmit;

import java.io.Serializable;
import lombok.Data;

@Data
public class QuestionSubmitAddRequest implements Serializable {

    private String language;

    private String code;

    private Long questionId;

    private static final long serialVersionUID = 1L;
}
