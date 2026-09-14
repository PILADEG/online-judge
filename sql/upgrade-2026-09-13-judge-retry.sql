-- 判题重试 / 终态改造：对【已存在】的库执行（新建库直接用 create_table.sql）
-- 背景：judge 判题失败不再直接丢消息，而是由 status 状态机驱动重试；
--       重试次数耗尽后置终态 4（系统故障待人工处理）。
use onlinejudge;

-- 1) 新增重试计数字段（每次重投前 +1；达到上限后提交置为状态 4）
alter table question_submit
    add column retryCount int default 0 not null comment '判题系统故障重试次数' after status;

-- 2) 更新状态注释（新增 4-系统故障重试耗尽待人工处理）
alter table question_submit
    modify column status int default 0 not null comment '状态（0-待判题 1-判题中 2-成功 3-失败 4-系统故障重试耗尽待人工处理）';

-- 3) 排查用 SQL（可选）
--    待人工处理的提交（重试耗尽的终态）：
--      select id, questionId, retryCount, judgeInfo, updateTime from question_submit where status = 4 and isDelete = 0;
--    历史上卡在"判题中"的僵尸提交（新版 JudgeRetryTask 会自动复位，可手工确认）：
--      select id, questionId, updateTime from question_submit where status = 1 and isDelete = 0;


-- ============================================================
-- 死信队列（DLQ）改造：2026-09-14
-- ============================================================

-- 4) 死信消息存档表（判题消息被拒收时落库，供人工排查）
create table if not exists dlq_message
(
    id          bigint auto_increment comment 'id' primary key,
    sourceQueue varchar(256)                          not null comment '来源队列',
    messageId   varchar(256)                          null comment '消息 id（生产者未设置时为 null）',
    body        text                                  not null comment '原始消息体',
    deathReason varchar(64)                           null comment '死信原因（x-death.reason：rejected/expired/maxlen）',
    deathCount  int         default 0                 not null comment '被死信化的次数（x-death.count）',
    createTime  datetime    default CURRENT_TIMESTAMP not null comment '入库时间',
    index idx_createTime (createTime)
) comment '死信消息存档' collate = utf8mb4_unicode_ci;

-- 5) ⚠️ RabbitMQ 队列参数不可变：主队列 code-queue 新增了 x-dead-letter-* 参数，
--    必须在 broker 上先删除旧队列再由服务启动重建，否则服务启动会报：
--      PRECONDITION_FAILED - inequivalent arg 'x-dead-letter-exchange' for queue 'code-queue'
--    步骤（先确认积压为 0，避免丢消息）：
--      curl -u guest:guest http://127.0.0.1:15672/api/queues/%2F/code-queue     # 看 messages 数
--      curl -u guest:guest -XDELETE http://127.0.0.1:15672/api/queues/%2F/code-queue
--    说明：即使这里丢了消息也不会丢数据——question_submit.status 仍是 WAITING，
--          JudgeRetryTask 会自动重投（这正是"DB 是真相"的好处）。

-- 6) 排查用 SQL（死信与重试）
--    死信存档（最近 50 条）：
--      select id, sourceQueue, deathReason, deathCount, createTime, left(body, 200) from dlq_message order by createTime desc limit 50;
--    进行中的重试：
--      select id, questionId, retryCount, judgeInfo, updateTime from question_submit where status in (0,1) and retryCount > 0;
