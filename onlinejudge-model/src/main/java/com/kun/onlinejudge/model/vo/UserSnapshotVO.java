package com.kun.onlinejudge.model.vo;

import java.io.Serializable;
import lombok.Data;

@Data
public class UserSnapshotVO implements Serializable {

    private Long id;
    private String userRole;
    private String userName;
    private String userAvatar;

    private static final long serialVersionUID = 1L;
}
