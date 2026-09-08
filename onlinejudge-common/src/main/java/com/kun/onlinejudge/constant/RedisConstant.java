package com.kun.onlinejudge.constant;

import cn.hutool.core.util.StrUtil;

public interface RedisConstant {
    String SESSION_KEY_PREFIX = "session:";
    String KICKED_KEY_PREFIX = "kicked:";
    static String getSessionKey(Long userId, String deviceType) {
        return SESSION_KEY_PREFIX + userId + ":" + deviceType;
    }
    static String getKickedKey(Long userId, String deviceType, String tokenId) {
        return KICKED_KEY_PREFIX + userId + ":" + deviceType + ":" + tokenId;
    }
}
