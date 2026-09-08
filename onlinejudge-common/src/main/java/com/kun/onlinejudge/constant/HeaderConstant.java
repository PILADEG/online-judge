package com.kun.onlinejudge.constant;

/**
 * 网关注入的身份头名（服务侧据此信任当前登录用户）
 */
public interface HeaderConstant {

    String HEADER_USER_ID = "X-User-Id";
    String HEADER_USER_ROLE = "X-User-Role";
    String HEADER_USER_NAME = "X-User-Name";
    String HEADER_DEVICE_TYPE = "X-Device-Type";

    // 与 utils.UserContext 读取的请求属性键保持一致
    String ATTR_USER_ID = "JWT_USER_ID";
    String ATTR_USER_ROLE = "JWT_USER_ROLE";
    String ATTR_USER_NAME = "JWT_USER_NAME";
    String ATTR_DEVICE_TYPE = "JWT_DEVICE_TYPE";
}
