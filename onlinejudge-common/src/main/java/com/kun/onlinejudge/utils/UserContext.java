package com.kun.onlinejudge.utils;

import com.kun.onlinejudge.model.vo.LoginUserVO;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

public class UserContext {
    private static final String ATTR_USER_ID = "JWT_USER_ID";
    private static final String ATTR_USER_ROLE = "JWT_USER_ROLE";
    private static final String ATTR_USER_NAME = "JWT_USER_NAME";
    private static final String ATTR_USER_AVATAR = "JWT_USER_AVATAR";
    private static final String ATTR_UNION_ID = "JWT_UNION_ID";
    private static final String ATTR_MP_OPEN_ID = "JWT_MP_OPEN_ID";
    private static final String ATTR_DEVICE_TYPE = "JWT_DEVICE_TYPE";

    private UserContext() {}

    private static HttpServletRequest getRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attrs).getRequest();
        }
        return null;
    }

    public static Long getUserId() {
        HttpServletRequest request = getRequest();
        return request != null ? (Long) request.getAttribute(ATTR_USER_ID) : null;
    }

    public static String getUserRole() {
        HttpServletRequest request = getRequest();
        return request != null ? (String) request.getAttribute(ATTR_USER_ROLE) : null;
    }

    public static String getUserName() {
        HttpServletRequest request = getRequest();
        return request != null ? (String) request.getAttribute(ATTR_USER_NAME) : null;
    }

    public static String getUserAvatar() {
        HttpServletRequest request = getRequest();
        return request != null ? (String) request.getAttribute(ATTR_USER_AVATAR) : null;
    }

    public static String getDeviceType() {
        HttpServletRequest request = getRequest();
        return request != null ? (String) request.getAttribute(ATTR_DEVICE_TYPE) : null;
    }

    public static LoginUserVO getLoginUser() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        Long userId = (Long) request.getAttribute(ATTR_USER_ID);
        if (userId == null) {
            return null;
        }
        LoginUserVO vo = new LoginUserVO();
        vo.setId(userId);
        vo.setUserRole((String) request.getAttribute(ATTR_USER_ROLE));
        vo.setUserName((String) request.getAttribute(ATTR_USER_NAME));
        vo.setUserAvatar((String) request.getAttribute(ATTR_USER_AVATAR));
        return vo;
    }
}
