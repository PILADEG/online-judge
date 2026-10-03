package com.kun.onlinejudge.model.dto.user;

import java.io.Serializable;
import lombok.Data;

@Data
public class UserAddRequest implements Serializable {

    private String userName;

    private String userAccount;

    private String userAvatar;

    private String userRole;

    private static final long serialVersionUID = 1L;
}