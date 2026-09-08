package com.kun.onlinejudge.model.enums;

import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;
public enum SupportLanguageEnum {
    JAVA("java", "Java"),
    CPP("cpp", "C++");

    private final String value;

    private final String text;

    SupportLanguageEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }
    public static List<SupportLanguageEnum> getAllEnums(){
        return Arrays.asList(SupportLanguageEnum.values());
    }
    /**
     * 根据 value 获取枚举
     *
     * @param value
     * @return
     */
    public static SupportLanguageEnum getEnumByValue(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (SupportLanguageEnum anEnum : SupportLanguageEnum.values()) {
            if (anEnum.getValue().equals(value)) {
                return anEnum; // Changed from anEnum to anEnum.getValue()
            }
        }
        return null;
    }

    public String getValue() {
        return value;
    }

    public String getText() {
        return text;
    }
}
