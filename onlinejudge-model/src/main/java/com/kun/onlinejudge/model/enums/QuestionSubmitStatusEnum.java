package com.kun.onlinejudge.model.enums;

import org.apache.commons.lang3.ObjectUtils;

public enum QuestionSubmitStatusEnum {

    WAITING(0, "待判题"),
    RUNNING(1, "判题中"),
    SUCCEED(2, "成功"),
    FAILED(3, "失败"),
    RETRY_EXHAUSTED(4, "系统故障，重试耗尽待人工处理");

    private final Integer value;

    private final String text;

    QuestionSubmitStatusEnum(Integer value, String text) {
        this.value = value;
        this.text = text;
    }

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
