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

    long userRegister(String userAccount, String userPassword, String checkPassword);

    UserLoginResponse userLogin(String userAccount, String userPassword, String userAgent);

    User getLoginUser();

    User getLoginUserPermitNull();

    boolean isAdmin();

    boolean isAdmin(User user);

    boolean userLogout();

    boolean userLogout(String refreshToken);

    LoginUserVO getLoginUserVO(User user);

    UserVO getUserVO(User user);

    List<UserVO> getUserVO(List<User> userList);

    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

}
