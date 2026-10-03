package com.kun.onlinejudge.model.dto.user;

import java.io.Serializable;
import lombok.Data;

@Data
public class UserUpdateRequest implements Serializable {
    private Long id;

    private String userName;

    private String userAvatar;

    private String userProfile;

    private String userRole;

    private static final long serialVersionUID = 1L;
}