package com.kun.onlinejudge.model.entity;

import com.kun.onlinejudge.model.vo.LoginUserVO;
import lombok.Data;

@Data
public class UserLoginResponse {
    private String accessToken;

    private String refreshToken;

    private LoginUserVO loginUserVO;

    private static final long serialVersionUID = 1L;
}
