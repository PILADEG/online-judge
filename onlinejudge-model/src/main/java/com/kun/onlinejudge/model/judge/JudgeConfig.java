package com.kun.onlinejudge.model.judge;

import lombok.Data;

@Data
public class JudgeConfig {

    private Long timeLimit;

    private Long memoryLimit;
    private Integer judgeType;
}
