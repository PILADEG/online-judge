


-- 切换库
use onlinejudge;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           unique  not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    unionId      varchar(256)                           null comment '微信开放平台id',
    mpOpenId     varchar(256)                           null comment '公众号openId',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin/ban',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    index idx_unionId (unionId)
) comment '用户' collate = utf8mb4_unicode_ci;

create table if not exists question
(
    id         bigint auto_increment comment 'id' primary key,
    title      varchar(256)                       null comment '标题',
    content    text                               null comment '内容',
    tags       varchar(1024)                      null comment '标签列表（json 数组）',
    answer     text                               null comment '题目答案',
    submitNum   int     default 0                 null comment '提交数',
    acceptedNum int     default 0                 null comment '通过数',
    judgeCases  text                               null comment '判断用例(json数组)',
    judgeConfig text                               null comment '判题配置(json对象)',
    userId     bigint                             not null comment '创建用户 id',
    editTime   datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    index idx_title (title),
    index idx_userId (userId)
) comment '题目' collate = utf8mb4_unicode_ci;

create table if not exists question_submit
(
    id         bigint auto_increment comment 'id' primary key,
    language varchar(256)                         not null comment '语言',
    code     text                                 not null comment '提交代码',
    judgeInfo text                                 null comment '判题信息(json对象)',
    status    int default 0                 not null comment '状态（0-待判题 1-判题中 2-成功 3-失败 4-系统故障重试耗尽待人工处理）',
    retryCount int default 0                not null comment '判题系统故障重试次数',
    questionId bigint                             not null comment '题目 id',
    userId     bigint                             not null comment '创建用户 id',
    editTime   datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    index idx_questionId (questionId),
    index idx_userId (userId)
) comment '题目提交表' collate = utf8mb4_unicode_ci;

-- 死信消息存档表（判题消息被拒收时由 DlqMessageConsumer 落库，供人工排查；append-only，无逻辑删除）
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
