package com.kun.onlinejudge.model.enums;

import org.apache.commons.lang3.ObjectUtils;

/**
 * 题目提交状态枚举
 */
public enum QuestionSubmitStatusEnum {

    WAITING(0, "待判题"),
    RUNNING(1, "判题中"),
    SUCCEED(2, "成功"),
    FAILED(3, "失败"),
    /**
     * 系统故障（沙箱不可用、DB 异常、判题代码异常等）重试耗尽后的终态，需人工处理。
     * 与 FAILED(3) 的区别：FAILED 是"判题得出了未通过的结果"，本值是"根本没判出来"。
     */
    RETRY_EXHAUSTED(4, "系统故障，重试耗尽待人工处理");

    private final Integer value;

    private final String text;

    QuestionSubmitStatusEnum(Integer value, String text) {
        this.value = value;
        this.text = text;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value
     * @return
     */
    public static QuestionSubmitStatusEnum getEnumByValue(Integer value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (QuestionSubmitStatusEnum anEnum : QuestionSubmitStatusEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }

    public Integer getValue() {
        return value;
    }

    public String getText() {
        return text;
    }
}
