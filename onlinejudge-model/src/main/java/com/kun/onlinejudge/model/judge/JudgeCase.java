package com.kun.onlinejudge.model.judge;

import lombok.Data;

/**
 * 判题用例
 */
@Data
public class JudgeCase {

    /**
     * 输入用例
     */
    private String inputCase;

    /**
     * 输出用例
     */
    private String outputCase;
}
