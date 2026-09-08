package com.kun.onlinejudge.userservice.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kun.onlinejudge.model.dto.user.UserQueryRequest;
import com.kun.onlinejudge.model.entity.User;
import com.kun.onlinejudge.model.entity.UserLoginResponse;
import com.kun.onlinejudge.model.vo.LoginUserVO;
import com.kun.onlinejudge.model.vo.UserVO;
import java.util.List;

public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param userAgent    客户端 UA（用于设备类型识别）
     * @return 携带 access/refresh token 的登录响应
     */
    UserLoginResponse userLogin(String userAccount, String userPassword, String userAgent);

    /**
     * 获取当前登录用户（基于身份头/请求上下文，未登录抛 NOT_LOGIN）
     *
     * @return
     */
    User getLoginUser();

    /**
     * 获取当前登录用户（允许未登录）
     *
     * @return
     */
    User getLoginUserPermitNull();

    /**
     * 当前请求用户是否为管理员（基于身份头上下文角色）
     *
     * @return
     */
    boolean isAdmin();

    /**
     * 是否为管理员
     *
     * @param user
     * @return
     */
    boolean isAdmin(User user);

    /**
     * 用户注销（撤销当前设备会话，使 access/refresh token 失效）
     *
     * @return
     */
    boolean userLogout();

    /**
     * 用户注销（基于 refresh token 撤销指定会话）
     *
     * @param refreshToken
     * @return
     */
    boolean userLogout(String refreshToken);

    /**
     * 获取脱敏的已登录用户信息
     *
     * @return
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 获取脱敏的用户信息
     *
     * @param user
     * @return
     */
    UserVO getUserVO(User user);

    /**
     * 获取脱敏的用户信息
     *
     * @param userList
     * @return
     */
    List<UserVO> getUserVO(List<User> userList);

    /**
     * 获取查询条件
     *
     * @param userQueryRequest
     * @return
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

}
