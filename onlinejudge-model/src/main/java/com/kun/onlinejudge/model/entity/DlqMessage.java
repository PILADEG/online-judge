package com.kun.onlinejudge.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 死信消息存档（append-only，用于人工排查"无法处理的消息"）。
 *
 * <p>与判题重试无关：重试由 question_submit.status 状态机驱动；本表只回答
 * "有没有消息被判题消费者拒收过、原因是什么、原始报文长什么样"。
 */
@TableName(value = "dlq_message")
@Data
public class DlqMessage implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 来源队列
     */
    private String sourceQueue;

    /**
     * 消息 id（生产者未设置时为 null）
     */
    private String messageId;

    /**
     * 原始消息体
     */
    private String body;

    /**
     * 死信原因（来自 x-death：rejected / expired / maxlen）
     */
    private String deathReason;

    /**
     * 被死信化的次数（来自 x-death.count）
     */
    private Integer deathCount;

    /**
     * 入库时间
     */
    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
