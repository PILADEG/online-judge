package com.kun.onlinejudge.model.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 用户认证快照（供网关注入身份头前的最后一次权威校验）
 */
@Data
public class UserSnapshotVO implements Serializable {

    private Long id;
    private String userRole;   // user/admin/ban
    private String userName;
    private String userAvatar;

    private static final long serialVersionUID = 1L;
}
