package com.kun.onlinejudge.model.dto.question;

import com.kun.onlinejudge.model.request.PageRequest;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class QuestionQueryRequest extends PageRequest implements Serializable {

    private Long id;

    private Long notId;

    private String searchText;

    private String title;

    private String content;

    private List<String> tags;

    private Long userId;

    private static final long serialVersionUID = 1L;
}
