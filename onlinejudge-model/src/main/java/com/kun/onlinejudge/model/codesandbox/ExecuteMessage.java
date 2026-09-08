package com.kun.onlinejudge.model.codesandbox;

import com.kun.onlinejudge.model.judge.JudgeCase;
import com.kun.onlinejudge.model.judge.JudgeConfig;
import com.kun.onlinejudge.model.judge.JudgeInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExecuteMessage implements Serializable {
    private String message;

    private String errorMessage;

    private String language;

    private String code;

    private Long time;

    private Long memory;

    private List<JudgeCase> judgeCases;

    private JudgeConfig  judgeConfig;

    private static final long serialVersionUID = 1L;
}
