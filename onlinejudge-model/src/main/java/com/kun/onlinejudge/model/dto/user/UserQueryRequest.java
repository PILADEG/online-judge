package com.kun.onlinejudge.model.dto.user;

import com.kun.onlinejudge.model.request.PageRequest;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserQueryRequest extends PageRequest implements Serializable {
    private Long id;

    private String unionId;

    private String mpOpenId;

    private String userName;

    private String userProfile;

    private String userRole;

    private static final long serialVersionUID = 1L;
}