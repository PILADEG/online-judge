package com.kun.onlinejudge.model.request;

import java.io.Serializable;
import lombok.Data;

/**
 * 删除请求（迁自原 common.DeleteRequest）
 */
@Data
public class DeleteRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    private static final long serialVersionUID = 1L;
}
