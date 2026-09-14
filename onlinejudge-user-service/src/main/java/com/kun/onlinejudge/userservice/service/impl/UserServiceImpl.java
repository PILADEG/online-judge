package com.kun.onlinejudge.userservice.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kun.onlinejudge.constant.CommonConstant;
import com.kun.onlinejudge.constant.RedisConstant;
import com.kun.onlinejudge.exception.BusinessException;
import com.kun.onlinejudge.model.auth.JwtUtils;
import com.kun.onlinejudge.model.dto.user.RefreshTokenResult;
import com.kun.onlinejudge.model.dto.user.UserQueryRequest;
import com.kun.onlinejudge.model.entity.User;
import com.kun.onlinejudge.model.entity.UserLoginResponse;
import com.kun.onlinejudge.model.enums.UserRoleEnum;
import com.kun.onlinejudge.model.result.ErrorCode;
import com.kun.onlinejudge.model.vo.LoginUserVO;
import com.kun.onlinejudge.model.vo.UserVO;
import com.kun.onlinejudge.userservice.mapper.UserMapper;
import com.kun.onlinejudge.userservice.service.UserService;
import com.kun.onlinejudge.utils.DeviceUtils;
import com.kun.onlinejudge.utils.SqlUtils;
import com.kun.onlinejudge.utils.UserContext;
import io.jsonwebtoken.Claims;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    public static final String SALT = "kun";
    @Resource(name = "scriptRedissonClient")
    private RedissonClient redissonClient;
    @Resource
    private JwtUtils jwtUtils;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 4 || checkPassword.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        // 密码和校验密码相同
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        RLock lock = redissonClient.getLock(RedisConstant.getRegisterLockKey(userAccount));
        Boolean isLocked = false;
        try {
            // 账户不能重复
            isLocked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "账号正在注册中，请稍等");
            }
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userAccount", userAccount);
            long count = this.baseMapper.selectCount(queryWrapper);
            if (count > 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
            }
            // 2. 加密
            String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
            // 3. 插入数据
            User user = new User();
            user.setUserAccount(userAccount);
            user.setUserPassword(encryptPassword);
            boolean saveResult = this.save(user);
            if (!saveResult) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，系统错误");
            }
            return user.getId();
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，系统错误");
        }finally {
            if (lock != null && isLocked && lock.isHeldByCurrentThread()){
                try {
                    lock.unlock();
                } catch (Exception e) {
                    log.error("redisson unlock error", e);
                }
            }
        }
    }

    @Override
    public UserLoginResponse userLogin(String userAccount, String userPassword, String userAgent) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        String deviceType = DeviceUtils.detectDeviceType(userAgent);
        return buildLoginResponse(user, deviceType);
    }

    /**
     * 获取当前登录用户
     * 身份来自 identity-header（IdentityHeaderFilter 已把 X-User-* 写入请求属性）
     *
     * @return
     */
    @Override
    public User getLoginUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        User currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    /**
     * 获取当前登录用户（允许未登录）
     * 身份来自 identity-header（IdentityHeaderFilter 已把 X-User-* 写入请求属性）
     *
     * @return
     */
    @Override
    public User getLoginUserPermitNull() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return null;
        }
        return this.getById(userId);
    }

    /**
     * 当前请求用户是否为管理员（身份头上下文角色）
     *
     * @return
     */
    @Override
    public boolean isAdmin() {
        String userRole = UserContext.getUserRole();
        return UserRoleEnum.ADMIN.getValue().equals(userRole);
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    /**
     * 用户注销（基于当前身份头会话：删除该设备的 Redis 会话，使 access/refresh token 立即失效）
     *
     * @return
     */
    @Override
    public boolean userLogout() {
        Long userId = UserContext.getUserId();
        if (userId != null) {
            String deviceType = UserContext.getDeviceType();
            if (deviceType != null) {
                redissonClient.getBucket(RedisConstant.getSessionKey(userId, deviceType)).delete();
            }
        }
        return true;
    }

    @Override
    public boolean userLogout(String refreshToken) {
        if (StringUtils.isBlank(refreshToken)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        try {
            Claims claims = jwtUtils.parseRefreshToken(refreshToken);
            Long userId = jwtUtils.getUserId(claims);
            String tokenId = jwtUtils.getTokenId(claims);
            String deviceType = jwtUtils.getDeviceType(claims);
            String sessionKey = RedisConstant.getSessionKey(userId, deviceType);
            String activeTokenId = (String) redissonClient.getBucket(sessionKey).get();
            // 仅当此 refresh token 是当前活跃会话时才删除 session key
            // 已被踢下线的 token 调用 logout 则不做任何操作
            if (tokenId != null && tokenId.equals(activeTokenId)) {
                redissonClient.getBucket(sessionKey).delete();
            }
        } catch (Exception e) {
            log.info("logout with invalid refresh token: {}", e.getMessage());
        }
        return true;
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVO(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String unionId = userQueryRequest.getUnionId();
        String mpOpenId = userQueryRequest.getMpOpenId();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(StringUtils.isNotBlank(unionId), "unionId", unionId);
        queryWrapper.eq(StringUtils.isNotBlank(mpOpenId), "mpOpenId", mpOpenId);
        queryWrapper.eq(StringUtils.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StringUtils.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.like(StringUtils.isNotBlank(userName), "userName", userName);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }
    private UserLoginResponse buildLoginResponse(User user, String deviceType) {
        String sessionTokenId = UUID.randomUUID().toString();

        // 检测同设备类型是否存在旧会话，若有则标记为 kicked（不可逆）
        String sessionKey = RedisConstant.getSessionKey(user.getId(), deviceType);
        String oldTokenId = (String) redissonClient.getBucket(sessionKey).get();
        if (oldTokenId != null) {
            long ttl = redissonClient.getBucket(sessionKey).remainTimeToLive() / 1000;
            if (ttl > 0) {
                String kickedKey = RedisConstant.getKickedKey(user.getId(), deviceType, oldTokenId);
                redissonClient.getBucket(kickedKey).set("true", ttl, TimeUnit.SECONDS);
            }
        }

        // 设置新会话
        redissonClient.getBucket(sessionKey).set(sessionTokenId, 7, TimeUnit.DAYS);

        // 生成令牌
        String accessToken = jwtUtils.generateAccessToken(user, sessionTokenId, deviceType);
        RefreshTokenResult refreshResult = jwtUtils.generateRefreshToken(user, deviceType, sessionTokenId);

        UserLoginResponse response = new UserLoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshResult.getToken());
        response.setLoginUserVO(getLoginUserVO(user));
        return response;
    }
}
