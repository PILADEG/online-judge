package com.kun.onlinejudge.model.enums;

import org.apache.commons.lang3.ObjectUtils;

/**
 * 题目提交状态枚举
 */
public enum QuestionSubmitStatusEnum {

    WAITING(0, "待判题"),
    RUNNING(1, "判题中"),
    SUCCEED(2, "成功"),
    FAILED(3, "失败");

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
