package com.kun.onlinejudge.security;

import com.kun.onlinejudge.annotation.AuthCheck;
import com.kun.onlinejudge.exception.BusinessException;
import com.kun.onlinejudge.model.enums.UserRoleEnum;
import com.kun.onlinejudge.model.result.ErrorCode;
import com.kun.onlinejudge.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 角色权限切面（语义与单体 AuthInterceptor 一致，但依赖身份头上下文而非 UserService）
 */
@Aspect
@Component
@Slf4j
public class AuthRoleAspect {

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        String userRole = UserContext.getUserRole();
        if (userRole == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(userRole);
        if (userRoleEnum == null || UserRoleEnum.BAN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        if (UserRoleEnum.USER.equals(mustRoleEnum)
                && !UserRoleEnum.USER.equals(userRoleEnum)
                && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return joinPoint.proceed();
    }
}
