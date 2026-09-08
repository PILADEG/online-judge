package com.kun.onlinejudge.model.enums;

import org.apache.commons.lang3.ObjectUtils;

public enum JudgeTypeEnum {
    STANDARD(0, "标准"),
    SPECIAL(1, "特殊"),
    FLOAT(2, "浮点数");

    private final Integer value;

    private final String text;

    JudgeTypeEnum(Integer value, String text) {
        this.value = value;
        this.text = text;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value
     * @return
     */
    public static JudgeTypeEnum getEnumByValue(Integer value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (JudgeTypeEnum anEnum : JudgeTypeEnum.values()) {
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
