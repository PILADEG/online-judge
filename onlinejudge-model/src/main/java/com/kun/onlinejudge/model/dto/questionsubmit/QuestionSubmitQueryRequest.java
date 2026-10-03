package com.kun.onlinejudge.model.dto.questionsubmit;

import com.kun.onlinejudge.model.request.PageRequest;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class QuestionSubmitQueryRequest extends PageRequest implements Serializable {

    private Long id;

    private String language;

    private Integer status;

    private Long questionId;

    private Long userId;

    private static final long serialVersionUID = 1L;
}
