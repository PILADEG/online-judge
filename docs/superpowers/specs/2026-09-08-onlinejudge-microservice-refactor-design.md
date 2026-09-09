# onlinejudge 单体 → Spring Cloud Alibaba 微服务改造设计

- 日期：2026-09-08
- 状态：已与用户逐节确认并定稿
- 单体来源：`D:\Java_project\onlineJudge`（Spring Boot 2.7.2 / Java 8 / MyBatis-Plus 3.5.2，单库 `onlinejudge`）
- 落地目录：`D:\Java_project\onlineJudge-cloud\onlineJudge`（已含骨架，本文档对该骨架做对齐与重排）

---

## 1. 背景与目标

把在线判题单体后端改造为基于 Spring Cloud Alibaba 的微服务，落地到 `onlineJudge-cloud/onlineJudge`。
目标：**核心闭环微服务化**（用户 / 题目 / 提交 / 判题 + 网关统一鉴权），维持对外 `/api` 契约不变（前端 / Postman 不感知）。

用户已拍板的决策（本轮全部确认）：
1. 版本路线：**Java 8 + Spring Boot 2.6.13 + Spring Cloud Alibaba 2021.0.5.0**（贴近单体、代码改动最小）。
2. 数据：**先单库后分库**——所有服务暂共用 `onlinejudge` 单库；judge 对 question/question_submit 的跨表原子更新保留同库直连，不引入分布式事务。
3. 功能范围：核心闭环优先。**微信/公众号、COS 云存储不再对外提供**；`FileController`/`CosManager` 收拢到 common 一个停用包"摆着先"，不注册 bean、不进网关路由。
4. 鉴权：**网关统一校验**（Gateway 解析 JWT + 会话校验，把身份以请求头透传给下游服务；服务只信请求头）。
5. 拆分粒度：**方案 A——经典四业务服务**（user / question / submit / judge）+ gateway + common/model/service-client 三个库模块。
6. 本轮交付：方案对比 + 推荐已定；本文档为最终设计，审阅通过后转入 writing-plans 出实施计划。

---

## 2. 现状结论

### 2.1 单体项目
- 技术栈：Spring Boot 2.7.2 + Java 8 + MyBatis-Plus 3.5.2 + Redisson + RabbitMQ + jjwt + 腾讯 COS + 微信公众号 + knife4j + EasyExcel + Hutool。
- 库：单库 `onlinejudge`，业务表仅 `user` / `question` / `question_submit` 三张（另有 post 模板残留表，代码未用，可忽略不迁）。
- Controller 分域清晰：`/user`、`/question`、`/question_submit`、`/file`、wxmp 根回调；统一 `context-path=/api`。
- 判题链路已是**异步解耦**形态：
  1. `POST /api/question_submit/add` 校验（题目必须存在）→ 写 DB `status=WAITING` → 发 RabbitMQ `code-queue`；
  2. Consumer 消费 → `JudgeServiceImpl.doJudge`：乐观锁 `UPDATE question_submit SET status=RUNNING WHERE id=? AND status=WAITING`（天然幂等）→ 直连 `question` 表取用例/配置 → `CodeSandBoxFactory` → Docker 沙箱 HTTP 执行 → `JavaJudge/StandardJudge` 比对；
  3. `JudgeUtils` 原子回写 `question.submitNum/acceptedNum + 1` 与 `question_submit.status/judgeInfo`，全程 `@Transactional`；
  4. 结果不推送，前端轮询 `GET /question_submit/get/vo`、`/my/list/page/vo`。
- 登录态：JWT(access 15min / refresh 7d) + Redis(Redisson) 会话簿记（`session:<userId>:<deviceType>` = tokenId、`kicked:` 标记）；鉴权 = `JwtInterceptor`(HandlerInterceptor，含过期自动续签) + `@AuthCheck`(AOP)。会话信息与角色 claims 均可用作网关校验依据。
- VO 拼装的跨域引用（已精读确认）：
  - `QuestionVO` 与 `QuestionSubmitVO` 都只额外补 **User**（见 `QuestionServiceImpl:116-152`、`QuestionSubmitServiceImpl:96-147`）；`QuestionSubmitVO` 不拼题目标题。
  - submit 在 add 时仅需"题目是否存在"（`QuestionSubmitServiceImpl:60`）。

### 2.2 目标目录骨架问题（今天刚搭，未对齐）
- 顶层聚合 pom：Java8 + SB 2.6.13 + SCA 2021.0.5.0，但 `modules` 只登记 common/model；dependencyManagement 残留引用不存在的 `onlinejudge-web/service/start`。
- 六个服务模块（gateway/judge/question/submit/user/service-client）被 Spring Initializr 默认生成为 **SB 4.1.1 + Java 17 + Spring Cloud 2025.1.3**，parent 指向 SB4、未挂入聚合 pom，且 **SCA 无对应 SB4 版本**（与"基于 SCA"矛盾）。
- common/model 里 `maven.compiler.source/target=25`（本机无 JDK 25）。

### 2.3 本机环境
`JAVA_HOME=JDK17`、Maven 3.9.11（在 17 下运行）；PATH 另有 Java 8。
→ Java 8 目标可用 `release 8`（在 JDK17 下编译 OK）；SB4 模块与 SCA 目标相悖，须重排。

### 2.4 结论
拆分难点不在异步（MQ 已解耦、JWT 会话已具备多实例基础），而在：(a) judge 对 question/question_submit 双表 Mapper 直连（先单库=保留直连，风险收敛）；(b) question/submit 拼 VO 对 user 资料的跨域引用（转 Feign）；(c) 提交时"题目存在"校验的跨域引用（转 Feign）；(d) 网关化后的登录态/权限校验（网关统一校验）。

---

## 3. 目标架构与版本基线

```
                     ┌────────────────────────────┐
   前端/Postman ──► │  onlinejudge-gateway  :8888 │  统一鉴权 + 路由 + CORS
   (契约 /api/**)   │  (Spring Cloud Gateway)    │
                     └──────┬───────┬───────┬─────┘
          Nacos 注册发现     │       │       │
              ┌─────────────▼┐ ┌────▼───────┐ ┌──▼──────────────┐
              │user-service  │ │question-   │ │ submit-service  │
              │  :8101       │ │service :8102│ │  :8103          │
              │ 含 /inner/user│ │含/inner/qstn│ │ 含 MQ Producer  │
              └──────────────┘ └────────────┘ └─────────────────┘
                                    │  RabbitMQ code-queue
                                    ▼
                        ┌─────────────────────┐
                        │ judge-service :8104 │  无 HTTP；消费判题；同库直连
                        │ (沙箱 HTTP 客户端)    │  question/question_submit
                        └─────────────────────┘
   公共库：onlinejudge-common / onlinejudge-model / onlinejudge-service-client
   中间件：Nacos | MySQL(onlinejudge 单库) | Redis | RabbitMQ | Docker沙箱(prod) | Sentinel(预留)
```

### 版本坐标（统一，删掉一切 SB4 痕迹）
| 项 | 版本 |
|---|---|
| Java | 1.8（聚合 pom 全局 `<java.version>1.8</java.version>`，common/model 的 25 改回 1.8） |
| Spring Boot | 2.6.13 |
| Spring Cloud | 2021.0.5（`spring-cloud-dependencies` import） |
| Spring Cloud Alibaba | 2021.0.5.0（`spring-cloud-alibaba-dependencies` import） |
| MyBatis-Plus | 复用单体版本坐标（3.5.2，SB2 用 `mybatis-plus-boot-starter`） |
| MySQL 驱动 | `com.mysql:mysql-connector-j`（SB2.6 已管理） |
| 其余 | jjwt/redisson/hutool/commons-lang3 等沿用单体坐标收进 common 或按需放入各服务 |

> 单体本身是 SB 2.7.2；目标 SB 2.6.13 为 SCA 2021.0.5.0 官方基线，业务代码 Java 8 兼容，迁移改动预期极小。若编译期遇到 spring 版本差异导致的问题，以 2.7.2 与 SCA 兼容基线为准再校准（SCA 2021.0.5.0 官方对 Spring Boot 2.6.x；可验证后微调）。

---

## 4. 模块与服务划分

### 4.1 目标目录树
```
onlineJudge-cloud/onlineJudge/          （聚合 pom，packaging=pom）
├─ onlinejudge-common                   通用库（所有服务 + 网关依赖）
├─ onlinejudge-model                    数据模型库（纯数据）
├─ onlinejudge-service-client           Feign 接口库（依赖 model）
├─ onlinejudge-gateway                  Spring Cloud Gateway（依赖 common + Nacos）
├─ onlinejudge-user-service
├─ onlinejudge-question-service
├─ onlinejudge-submit-service
└─ onlinejudge-judge-service
```
公共库与被依赖关系：`model`（无框架依赖）← `common` ← 服务 / 网关；`model` ← `service-client` ← 服务。

### 4.2 各模块内容（对照单体包搬运）
| 模块 | 收入内容 | 关键外部依赖 |
|---|---|---|
| **common** | `common/`(BaseResponse、ErrorCode、ResultUtils、PageRequest、DeleteRequest)、`exception/`、`constant/`、`annotation/AuthCheck`、`aop/`(AuthInterceptor、LogInterceptor)、**身份头解析组件**(IdentityHeaderFilter + UserContextHolder，见 §6)、`config/`(Cors、MyBatisPlus、Json 等通用)、`utils/`(JwtUtils、NetUtils、SqlUtils、SpringContextUtils、DeviceUtils)、Rabbit 常量（交换机/队列/路由键）、**停用包 `fileparked/`**(FileController、CosManager、COS 相关——只摆不启用) | webmvc、aop、jjwt、servlet、mybatis-plus(可选)、redisson(可选, 网关/user 用) |
| **model** | `model/entity`(User、Question、QuestionSubmit、UserLoginResponse)、`model/dto`、`model/vo`(UserVO、QuestionVO、QuestionSubmitVO)、`model/enums`、`model/judge`(JudgeCase、JudgeConfig、JudgeInfo 等)、`model/codesandbox`、判题/权限枚举与常量 | lombok/hutool 少量，**无框架重依赖** |
| **service-client** | Feign 接口：`UserServiceClient`、`QuestionServiceClient`（出入参复用 model 的 VO / BaseResponse） | model + openfeign |
| **gateway** | Spring Cloud Gateway 路由、统一鉴权 `GlobalFilter`、CORS、access-log | common、Nacos、loadbalancer、spring-cloud-starter-gateway(WebFlux)、sentinel-gateway(预留) |
| **user-service** | `UserController`、`UserService(+Impl)`、`UserMapper`+xml、Redis 会话簿记与 JWT 签发(登录/登出/踢人)、`/inner/user/**` VO 查询 | common、model、service-client、MySQL、Redis |
| **question-service** | `QuestionController`、`QuestionService(+Impl)`、`QuestionMapper`、`/inner/question/**`；VO 拼 user 改调 UserServiceClient | common、model、service-client、MySQL |
| **submit-service** | `QuestionSubmitController`、`QuestionSubmitService(+Impl)`、`QuestionSubmitMapper`、`messagelist/RabbitMqProducer`、Rabbit 配置；add 校验题目调 QuestionServiceClient、VO 拼 user 调 UserServiceClient | common、model、service-client、MySQL、AMQP |
| **judge-service** | `judgement/**`(Judge/JudgeStrategy/Factory/template)、`codesandbox/**`(沙箱 HTTP 客户端/代理/factory/example)、`messagelist/RabbitMqConsumer`(manual ack)、`utils/JudgeUtils`、**同库直连** QuestionMapper/QuestionSubmitMapper | common、model、MySQL、AMQP |

删除 / 收拢项：
- 删除：`wxmp/**`、`WxMpController`、cos 业务配置启用、`generate/CodeGenerator`（如不需要）、`judgement/test.java`(debug 控制器)。
- 收拢停用：`FileController`、`CosManager` 及 cos 配置类移入 common `fileparked` 包，不注册 Bean、不进路由。

### 4.3 拆分规则（关键约定）
1. **"当前登录用户 / 角色 / isAdmin"是请求上下文**：由网关注入身份头、各服务从身份头还原本地上下文，**禁止** Feign 到 user-service 查询"我是谁"。
2. **"给定 userId 取资料拼 VO"才是跨服务读**：Feign → user-service `/inner/user/**`。
3. **"/inner/** 只进服务间、不进网关**：Feign 直连实例，网络内部互信（后续可加服务间 token）。
4. **judge-service 无 HTTP、不进网关**；跨域写仍走同库直连（先单库决策）。

---

## 5. 服务间契约

### 5.1 Feign（同步、低频、读）
走 `/api/inner/**`（服务自带 context-path=/api），各服务对 inner 路径**不做用户鉴权**（网络内部互信）：

| 提供方 | 接口 | 语义 | 消费方 |
|---|---|---|---|
| user-service | `GET /api/inner/user/{id}/vo` | 单个 UserVO（服务端脱敏，不含密码等） | question、submit |
| user-service | `POST /api/inner/user/list/vo`（body=ids） | 批量 UserVO，用于分页 VO 一次性拼装 | question、submit |
| question-service | `GET /api/inner/question/{id}` | 去 answer/judgeCases 的 QuestionVO 或 404 | submit（校验题目存在） |
| question-service | `POST /api/inner/question/list`（可选） | 批量 | submit(如需) |

Feign 接口（service-client）：`UserServiceClient`、`QuestionServiceClient`。consumer 端把 inner 返回的非 0 `code` 还原为 `BusinessException`（契约语义不丢）。

### 5.2 RabbitMQ（submit → judge，异步）
- 交换机/队列/路由键沿用：DirectExchange `code-exchange`、Queue `code-queue`、routingKey `code.routing.key`；Producer 迁 submit-service，Consumer 迁 judge-service；常量收进 common 防漂移。
- 仍传 QuestionSubmit 实体 JSON；`acknowledge-mode: manual`，成功 `basicAck`、异常 `basicNack(requeue=false)` 保持现状。
- 跨服务不影响：队列只判题一个消费方，judge 幂等靠乐观锁 status 抢占。

### 5.3 同库直连（judge 回写，先单库决策）
保留 `JudgeServiceImpl` / `JudgeUtils` 对 `question`(`submitNum/acceptedNum + 1` 原子)与 `question_submit`(status/judgeInfo) 的直连与 `@Transactional`。这是有意为之的中间态；分库时改为"判题完成事件 + 最终一致(可接 Seata/MQ)"，见 §10 演进。

---

## 6. 网关与统一鉴权（本次改造新增部分）

### 6.1 目标
网关负责身份校验与会话管理，服务端只信网关注入的身份头 → 服务侧无需自持 Redis 会话校验，减少重复与不一致。

### 6.2 网关行为（Spring Cloud Gateway + WebFlux，GlobalFilter/WebFilter）
1. **鉴权边界**：网关只回答"请求是否来自已登录用户"。放行名单含确实免登录的入口：`OPTIONS` 预检、`POST /api/user/register`、`POST /api/user/login`、健康检查/探针；其余路径一律要求合法 token。放行名单最终与单体 `WebMvcConfig`/`JwtInterceptor` 白名单**逐条对齐**（仅移除 wx 项，避免游客可访问面发生变化）；端点级差异在实施计划步骤 7 核对。
2. **校验**：读 `Authorization: Bearer` → `JwtUtils` 验签 → 与 Redis 会话簿记核对（`session:<userId>:<deviceType>` 匹配、无 `kicked:`）→ 取 `userId/userRole` 等 claims。非法/过期且 refresh 可用 → 走**自动续签**（与单体 JwtInterceptor 同语义），并把新 token 写入响应头返回前端。
3. **注入身份头**：先**剥掉入站可能伪造的 `X-User-*` 请求头**，再写入 `X-User-Id`、`X-User-Role`（必要时 `X-Device-Type`）后放行到下游路由。
4. **错误统一返回**：失败返回 `BaseResponse`（code 沿用单体 `ErrorCode` 语义，如 NOT_LOGIN 40100 / KICKED_OFFLINE 等），保证前端判断逻辑不变。
5. **路由**：`/api/user/**→user-service`、`/api/question/**→question-service`、`/api/question_submit/**→submit-service`；**不路由 `/api/inner/**`**。
6. **认证与授权分离**：网关 = **认证**（token 有效、会话在线）；服务 = **授权**（`@AuthCheck` 依据身份头中的角色本地判定端点可否访问），避免网关维护端点-角色矩阵。

### 6.3 服务侧（common 提供）
- `IdentityHeaderFilter`：服务收到非 `/inner` 请求时读 `X-User-Id/X-User-Role` 写入请求级 `UserContext`（供 `@AuthCheck` AOP 与控制器取当前用户）；`/inner/**` 请求不要求身份头、直接放行（内部互信）。
- 原控制器/服务里 `userService.getLoginUser()/isAdmin()` 改为读 `UserContextHolder`（本地上下文）。code 隐藏逻辑（owner/admin 判断）用本地上下文完成。

### 6.4 安全边界（记录在案）
- 服务实例不得对公网暴露（仅网关入口）；`/inner/**` 依赖内网/服务网互信，**后续演进**加服务间共享凭据头或 mTLS（见 §10）。
- Redis 需求范围收敛为：gateway(user 会话校验)、user-service(登录/登出写会话)；question/submit/judge 服务**不依赖 Redis 做用户鉴权**。

---

## 7. 配置与中间件（多环境）

各服务 `application.yml` 含 dev/test/prod；dev 默认与单体 dev 段一致，仅新增 Nacos/服务名。

| 项 | dev 默认 |
|---|---|
| Nacos | `localhost:8848`（服务名 = 模块名，`onlinejudge-{gateway,user-service,...}`） |
| MySQL | 各业务服务同库 `jdbc:mysql://localhost:3306/onlinejudge`（沿用现有账号） |
| Redis | gateway/user-service 使用 `localhost:6379 db2`（会话簿记 key 沿用） |
| RabbitMQ | `localhost guest/guest`，ack 模式沿用 |
| 沙箱 | prod=`codesandbox.type=docker`、url 沿用；dev/test=`example`（无需真沙箱） |
| 端口 | gateway 8888；user 8101；question 8102；submit 8103；judge 8104 |
| Sentinel | 预留依赖；首批网关+submit 埋点，暂不配规则 |

各服务启动类位于各自模块根包；聚合 pom 的 spring-boot-maven-plugin 需允许各 service 独立 `repackage`（common/model/service-client 为库模块不 repackage，仅 `install`）。

---

## 8. 错误处理 / 日志 / 测试

- `GlobalExceptionHandler` + `BusinessException` + `ThrowUtils` 下沉 common，各服务同语义；网关层单独兜底（401/熔断时的统一 JSON）。
- `/inner` 返回 `BaseResponse`；consumer 端解码时把非 0 `code` 还原为 `BusinessException`。
- `LogInterceptor`/切面随服务；网关独立 access-log。
- 测试策略：
  - 单测随代码迁到各服务；
  - 为 VO 拼装、`/inner` 契约（提供方与 Feign 消费方字段一致）、"提交→判题→回写"闭环补契约/集成测试；
  - 网关统一鉴权补过滤器级测试（放行/鉴权/续签/伪造头剥离）。
- 端到端验收清单（迁移完成后人工 + Postman 回归）：
  1. register → login（拿 access/refresh）→ 校验访问受保护接口；
  2. admin 建题 → 游客/普通用户列表可见、详情脱敏（answer/judgeCases 不泄露）；
  3. 提交代码 → DB 落 WAITING → MQ 消费 → judge RUNNING →（example 沙箱 dev）→ 结果回写 → 轮询 get/vo 可见 status/judgeInfo（非本人不可见 code）；
  4. `question.submitNum/acceptedNum` 计数正确；重复消费不重判（乐观锁幂等）；
  5. 网关直连端口不暴露、`/inner` 不可经网关访问。

---

## 9. 实施顺序（8 步，先冒烟后铺开）

| 步骤 | 内容 | 退出标准 |
|---|---|---|
| 0 修复骨架 | 聚合 pom 收编 8 模块；删除 SB4.1.1 子 pom/`.mvn`；common/model `compiler→1.8`；清理 depMgmt 残留 `onlinejudge-web/service/start`；包名统一 `com.kun` | 空模块 `mvn install` 全绿 |
| 1 填库模块 | common → model → service-client 依序搬运并编译（含 Rabbit 常量、`/inner` Feign 接口占位） | `mvn install` 全绿 |
| 2 冒烟验证 | 4 服务 + 网关最小可运行（各 `/ping` + 一条 Feign 调用 + 一条 MQ），验证 Nacos 注册发现/网关路由/Feign/AMQP 在 SCA2021 栈上全通 | 冒烟脚本通过（去最大风险） |
| 3 user-service | 迁用户域 + `/inner/user/**` + Redis 会话/续签留在服务侧配合网关 | user 接口回归过 |
| 4 question-service | 迁题目域；VO 拼 user 换 UserServiceClient；新增 `/inner/question/**` | question 接口回归过 |
| 5 submit-service | 迁提交域；add 校验题目走 QuestionServiceClient；VO 拼 user 走 UserServiceClient；RabbitMqProducer | 提交落 WAITING + MQ 可见 |
| 6 judge-service | 迁判题/沙箱/Consumer/同库直连 Mapper | 判题闭环回写正确 |
| 7 gateway | 路由 + 统一鉴权过滤器 + CORS + 续签 + access-log | 见 §8 验收 1–2 |
| 8 端到端 | 完整闭环 + Postman 回归 + 清理残留 + 看门狗；git 初始化（如需） | 验收 1–5 全过 |

> 建议冒烟(步骤 2)完成后，git 在 `onlineJudge-cloud/onlineJudge` 初始化并逐步提交，形成可回滚里程碑。

---

## 10. 风险与演进

| 主题 | 处理 |
|---|---|
| 版本栈老（SCA2021.0.5.0/SB2.6.13） | 与单体一致、改动最小；Nacos/Sentinel 选官方匹配版本；预留升级 Java17+SB3+SCA2023 的路径（javax→jakarta 迁移清单后置） |
| SB4 骨架残留 | 步骤 0 彻底清理，避免混编 |
| 网关统一鉴权 | 最大新增面；用 §8 过滤器级测试覆盖；服务必须只经网关暴露 |
| 先单库耦合 | 明确是中间态；分库时 judge 回写改事件/最终一致，计数域归属 question-service |
| 判题幂等 | 依赖现有乐观锁，不因拆分改变 |
| wx/cos/file 去除 | file 停用包保留代码，"摆着"不启用；如未来要 file 上传，再以独立 file-service 或并入 user-service 演进 |
| 服务间安全 | `/inner` 内网互信为首版；演进加服务间 token / mTLS / 网关白名单策略 |
| 依赖卫生 | common 依赖 webmvc/jjwt/redisson 等会传染给 judge-service；judge-service 无 HTTP，落地时对 web 依赖做隔离（排除内嵌容器 / `web-application-type=none`），避免起多余端口 |

---

## 11. 非目标（本轮不做）

- 不引入 Seata 分布式事务（单库先跑）。
- 不做每服务独立库（先单库）。
- 不迁移/启用微信、公众号、COS 业务；file 仅停用保留。
- 不做网关级流控规则落地（Sentinel 依赖预留）。
- 不做 Kubernetes/容器编排（本地多进程 + Nacos 先行）；Dockerfile 后续按服务补充。

---

## 附：锁定决策回放（供评审核对）
1. Java8 + SB2.6.13 + SCA 2021.0.5.0；2. 先单库后分库；3. 核心闭环优先、wx/cos 去除、file 停用保留；4. 网关统一鉴权；5. 方案 A 四业务服务拆分；6. 本轮交付=方案+设计定稿。

## 修订（2026-09-09）：鉴权主干先行 + 每请求最新 user

用户于 Foundation 完成后进一步拍板，**调整实施顺序与鉴权语义**：

1. **鉴权主干先行**：把"网关统一鉴权 + 公共身份头（X-User-*）+ 服务侧身份头信任过滤器"从 §9 步骤 7 提前为下一步骤；各业务服务迁移全部落在已就绪的鉴权主干之上，避免每服务重复做本地 JWT 拦截（弃用"过渡期各服务自校验"方案）。
2. **每请求回查最新 user（保留即时封禁语义）**：单体 JwtInterceptor 每请求回 DB 取最新 role/userName 以即时生效封禁/改角色。微服务化后在**网关层**保留该语义：网关校验 token + Redis 活跃会话后，经 load-balanced WebClient 调 user-service 内网端点取最新用户快照（id/userRole/userName/账号状态），再注入 `X-User-Id/X-User-Role/X-User-Name` 等身份头；服务只信身份头、不再每请求查库。代价：每个已登录请求多一次 gateway→user-service 内网 RTT（可后续加短 TTL 缓存优化）。
3. **服务侧收敛**：服务（含 user-service 自身受保护端点）统一用 common 的"身份头过滤器 + @AuthCheck(按身份头角色)"；user-service 主要负责签发令牌/会话簿记（Redis/redisson）与对内网暴露用户快照/脱敏查询。
4. 其余锁定决策不变；修订后首个子计划为 **auth-backbone（网关统一鉴权 + 身份头公共组件 + user-service 最小登录签发/内网快照）**。

> 修订追记（2026-09-09 B2 落地后）：§4.1/§4.2 旧文"网关依赖 common"已失效——网关现只依赖 `onlinejudge-model`（model.result/model.auth）与自身 WebFlux/redis-reactive 栈；不依赖 common（其含 servlet starter）。B1 落 user-service 真实登录/会话/内网快照，B2 落网关统一鉴权（验签+Redis 会话+每请求快照+剥/注入 X-User-*+ban 硬拒+续签），鉴权主干闭环。

> 完成记录（2026-09-09）：核心闭环四服务 + 网关统一鉴权**全部迁移完成并通过整体端到端联测**——经网关真实 token 走通 注册→登录→admin 建题→题目 VO 脱敏→提交→MQ→judge(本地 example 沙箱)判题→DB 回写 status/judgeInfo/submitNum/acceptedNum；错码判 FAILED。服务：user/question/submit/judge 各司其职、网关为唯一入口（验签+Redis 会话+每请求快照+剥/注入 X-User-*+ban 硬拒）。生产部署注意事项：judge 需以 **JDK**（非 JRE）启动（dev 本地 javac 沙箱），且 **`codesandbox.type=docker`** 才具隔离（example 为 dev-only）。
