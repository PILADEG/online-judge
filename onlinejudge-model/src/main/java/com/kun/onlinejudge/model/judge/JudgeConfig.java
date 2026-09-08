package com.kun.onlinejudge.model.judge;

import lombok.Data;

/**
 * 判题配置
 */
@Data
public class JudgeConfig {

    /**
     * 时间限制（ms）
     */
    private Long timeLimit;

    /**
     * 内存限制（KB）
     */
    private Long memoryLimit;
    /**
     * 判题类型
     */
    private Integer judgeType;
}
