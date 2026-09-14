package com.kun.onlinejudge.constant;

import java.util.Locale;

public interface RedisConstant {
    String SESSION_KEY_PREFIX = "session:";
    String KICKED_KEY_PREFIX = "kicked:";
    String REGISTER_LOCK = "register:lock:";
    static String getSessionKey(Long userId, String deviceType) {
        return SESSION_KEY_PREFIX + userId + ":" + deviceType;
    }
    static String getKickedKey(Long userId, String deviceType, String tokenId) {
        return KICKED_KEY_PREFIX + userId + ":" + deviceType + ":" + tokenId;
    }
    static String getRegisterLockKey(String account) {
        account = account.toLowerCase(Locale.ROOT);
        return REGISTER_LOCK + account;
    }
}
