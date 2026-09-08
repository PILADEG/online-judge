package com.kun.onlinejudge.model.request;

import lombok.Data;

/**
 * 分页请求（迁自原 common.PageRequest；sortOrder 默认值内联，避免依赖 common）
 */
@Data
public class PageRequest {

    /**
     * 当前页号
     */
    private int current = 1;

    /**
     * 页面大小
     */
    private int pageSize = 10;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序顺序（默认升序，与 CommonConstant.SORT_ORDER_ASC 同值）
     */
    private String sortOrder = "ascend";
}
