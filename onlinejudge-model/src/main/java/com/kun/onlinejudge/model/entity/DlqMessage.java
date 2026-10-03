package com.kun.onlinejudge.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

@TableName(value = "dlq_message")
@Data
public class DlqMessage implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String sourceQueue;

    private String messageId;

    private String body;

    private String deathReason;

    private Integer deathCount;

    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
