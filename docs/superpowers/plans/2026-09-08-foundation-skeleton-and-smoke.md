# Foundation：骨架对齐 + 共享库 + 冒烟验证 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `D:\Java_project\onlineJudge-cloud\onlineJudge` 目前版本混乱的骨架，对齐为可整体 `mvn install` 的 Spring Cloud Alibaba 多模块工程；填入两个共享库（model/common）与 service-client 契约；用最小化的 5 个可运行模块证明 Nacos 注册发现 / 网关路由 / Feign / RabbitMQ 在 SCA 2021.0.5.0 上全通。

**Architecture:** 聚合 pom（Java8 + SB2.6.13 + SCA2021.0.5.0）收编 8 模块。共享层严格两库：`onlinejudge-model` 自包含纯数据（entity/dto/vo/enums/request 基类），`onlinejudge-common` 为 servlet 侧基础设施并依赖 model；`onlinejudge-service-client` 提供 Feign 契约。网关是 Spring Cloud Gateway(WebFlux)，**不依赖 common**。业务代码（controller/service/mapper）不在本计划内迁移，由后续按服务子计划搬入。

**Tech Stack:** Java 8、Spring Boot 2.6.13、Spring Cloud 2021.0.5、Spring Cloud Alibaba 2021.0.5.0、Spring Cloud Gateway、OpenFeign、Spring AMQP、Nacos、Lombok、MyBatis-Plus 注解、Hutool、jjwt、Redisson。

## Global Constraints（本计划与后续计划都必须遵守）

**版本与构建**
- Java 目标 `1.8`（本机 `JAVA_HOME=JDK17`，Maven 3.9.11 在 17 下运行，用 `-source/-target 1.8` 编译）。
- 版本唯一来源：聚合 pom `<properties>` → `spring-boot.version=2.6.13`、`spring-cloud.version=2021.0.5`、`spring-cloud-alibaba.version=2021.0.5.0`、`mybatis-plus.version=3.5.2`、`hutool.version=5.8.8`、`redisson.version=4.6.1`、`jjwt.version=0.11.5`。任何模块不得再引入 SB4/Java17/Spring Cloud 2025 依赖。
- 父 pom 无 `<dependencies>`（只有 `dependencyManagement` + build 插件），避免向所有子模块泄漏依赖。

**模块与依赖方向（相对设计文档的落地细化）**
- 模块构建顺序 = DAG：`model` ← `common` ← `service-client`；`common`/`service-client` 供 user/question/submit/judge 使用。**网关不依赖 common/service-client/model 之外的任何 servlet 库**（WebFlux 与 spring-webmvc 冲突）。
- `onlinejudge-model`：自包含、**不得 import** `com.kun.onlinejudge.common.*`（含 `.constant`、`.exception`、`.utils`）。三个 `*QueryRequest` 的分页基类改为 `com.kun.onlinejudge.model.request.PageRequest`。
- `PageRequest.sortOrder` 默认值改为内联字面量 `"ascend"`（与 `CommonConstant.SORT_ORDER_ASC` 同值），消除 model→common 引用。
- `common` 内不再迁移旧 `JwtInterceptor`/`AuthInterceptor`/`WebMvcConfig`/`RedissonConfig`/`MyBatisPlusConfig`（原因：它们依赖业务 `UserService` 或只在服务内启用；网关统一鉴权将用新的身份头过滤器替代，属后续服务子计划）。
- 删除不迁移：`wxmp/**`、`WxOpenConfig`、`CosClientConfig`、`manager/CosManager`、`controller/FileController`、`constant/FileConstant`、`judgement/test.java`、`generate/CodeGenerator`。文件上传 DTO `model/dto/file/UploadFileRequest` 随 model 保留（无端点，即"摆着"）。
- 可运行服务根包：`com.kun.onlinejudge.{gateway,userservice,questionservice,submitservice,judgeservice}`；共享库类保持原 `com.kun.onlinejudge.*` 包。服务只扫描自己的根包（本计划阶段不扫描 common 的 `@Configuration`，后续子计划按需选择性引入）。
- 服务名（Nacos/Feign 依此）：`onlinejudge-gateway`、`onlinejudge-user-service`、`onlinejudge-question-service`、`onlinejudge-submit-service`、`onlinejudge-judge-service`。
- HTTP 服务统一 `server.servlet.context-path: /api`（与单体一致）；网关路由**不剥前缀**，把 `/api/**` 原样转发给服务，服务在自己 `/api` 上下文内按原 controller 映射命中。`/api/inner/**` 不进网关，Feign 直连时 URL 自带 `/api` 前缀。

**库内目录约定**
- 原 `com.kun.onlinejudge.common.*` 中的 `PageRequest`/`DeleteRequest` 迁到 model 的 **`com.kun.onlinejudge.model.request`** 包；引用它们的业务 controller/service 在各自迁移子计划里同步改 import。
- model 依赖：lombok、hutool、`mybatis-plus-annotation`。
- common 依赖：model、`spring-boot-starter-web`、`spring-boot-starter-aop`、`hutool-all`、`commons-lang3`、jjwt 三件套、redisson（库）、jackson（随 web）。**不含** mybatis-plus / mysql / spring-data-redis / amqp（这些随业务功能由各服务子计划按需引入，避免本计划的冒烟被迫连 DB/Redis）。

**测试/提交**
- 每个任务以编译或运行命令通过为验收；每完成一个可独立交付的任务提交一次（本计划在 git 仓库内，Task 0 初始化）。
- 提交信息风格：`feat(foundation): <摘要>`。

---

### Task 0: 初始化 git 仓库与基础 .gitignore

**Files:**
- Create: `.gitignore`（已存在，追加 `logs/`、`*.log`）
- Run: `git init`、首次提交（记录当前未对齐骨架，便于后续 diff）

**Interfaces:**
- Produces: git 仓库基线；此后每个任务末尾 `git add <files>` + `git commit`。

- [ ] **Step 1: 确认工作目录**

Run: `cd D:/Java_project/onlineJudge-cloud/onlineJudge && pwd`
Expected: 输出该目录，且已确认本计划在其根目录执行。

- [ ] **Step 2: 追加忽略项并初始化**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
cat >> .gitignore <<'EOF'
logs/
*.log
EOF
git init -b main
git add -A
git commit -m "chore(foundation): init repo from unaligned cloud skeleton"
```
Expected: 首次提交成功（当前含 SB4 骨架的文件都被记录，后续 Task 2 清理可被 diff 追踪）。

---

### Task 1: 重写聚合父 pom

**Files:**
- Overwrite: `pom.xml`（聚合 pom）

**Interfaces:**
- Produces: 版本属性（`spring-boot.version` 等）、`dependencyManagement`、`pluginManagement`；被所有子模块 parent 引用。

- [ ] **Step 1: 用如下内容整体覆盖聚合 `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.kun</groupId>
    <artifactId>onlinejudge</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>onlinejudge</name>
    <description>onlinejudge 微服务改造（Spring Cloud Alibaba 2021）</description>

    <properties>
        <java.version>1.8</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
        <spring-boot.version>2.6.13</spring-boot.version>
        <spring-cloud.version>2021.0.5</spring-cloud.version>
        <spring-cloud-alibaba.version>2021.0.5.0</spring-cloud-alibaba.version>
        <mybatis-plus.version>3.5.2</mybatis-plus.version>
        <hutool.version>5.8.8</hutool.version>
        <redisson.version>4.6.1</redisson.version>
        <jjwt.version>0.11.5</jjwt.version>
    </properties>

    <modules>
        <module>onlinejudge-model</module>
        <module>onlinejudge-common</module>
        <module>onlinejudge-service-client</module>
        <module>onlinejudge-gateway</module>
        <module>onlinejudge-user-service</module>
        <module>onlinejudge-question-service</module>
        <module>onlinejudge-submit-service</module>
        <module>onlinejudge-judge-service</module>
    </modules>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>com.alibaba.cloud</groupId>
                <artifactId>spring-cloud-alibaba-dependencies</artifactId>
                <version>${spring-cloud-alibaba.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>com.baomidou</groupId>
                <artifactId>mybatis-plus-boot-starter</artifactId>
                <version>${mybatis-plus.version}</version>
            </dependency>
            <dependency>
                <groupId>com.baomidou</groupId>
                <artifactId>mybatis-plus-annotation</artifactId>
                <version>${mybatis-plus.version}</version>
            </dependency>
            <dependency>
                <groupId>cn.hutool</groupId>
                <artifactId>hutool-all</artifactId>
                <version>${hutool.version}</version>
            </dependency>
            <dependency>
                <groupId>org.redisson</groupId>
                <artifactId>redisson</artifactId>
                <version>${redisson.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-api</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-impl</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-jackson</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.8.1</version>
                    <configuration>
                        <source>1.8</source>
                        <target>1.8</target>
                        <encoding>UTF-8</encoding>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>${spring-boot.version}</version>
                </plugin>
            </plugins>
        </pluginManagement>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 提交聚合 pom（其余模块尚未对齐，先不构建）**

```bash
git add pom.xml
git commit -m "build(foundation): align aggregate pom to Java8 + SCA 2021.0.5.0, register 8 modules"
```

---

### Task 2: 重写全部子模块 pom 并清除 SB4 骨架残留

**Files:**
- Overwrite（8 个）：`onlinejudge-model/pom.xml`、`onlinejudge-common/pom.xml`、`onlinejudge-service-client/pom.xml`、`onlinejudge-gateway/pom.xml`、`onlinejudge-user-service/pom.xml`、`onlinejudge-question-service/pom.xml`、`onlinejudge-submit-service/pom.xml`、`onlinejudge-judge-service/pom.xml`
- Delete：各业务/网关模块下 SB4 残留的 `src/main/java/**/*Application.java`、`src/test/java/**/*ApplicationTests.java`、`src/main/resources/application.properties`、`.mvn/` 目录

**Interfaces:**
- Consumes: Task 1 的父 pom。
- Produces: 所有模块统一 parent=聚合 pom、可被 reactor 一起构建；空源码可 `mvn -DskipTests install` 通过。

- [ ] **Step 1: 清掉每个非库模块的 SB4 生成物（旧包名形如 `com.kun.onlinejudgegateway`，即 SB4 生成）**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
for m in onlinejudge-gateway onlinejudge-user-service onlinejudge-question-service \
         onlinejudge-submit-service onlinejudge-judge-service onlinejudge-service-client; do
  rm -rf "$m/.mvn"
  rm -f  "$m"/src/main/java/com/kun/onlinejudge*/*Application.java
  rm -f  "$m"/src/test/java/com/kun/onlinejudge*/*ApplicationTests.java
  rm -f  "$m/src/main/resources/application.properties"
done
find onlinejudge-common onlinejudge-model -type f
```
Expected: `rm` 对不存在的路径静默；common/model 目前只剩各自 pom.xml。执行后无残留 `*Application.java`。

- [ ] **Step 2: 覆盖库模块 pom**

`onlinejudge-model/pom.xml` 覆盖为：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.kun</groupId>
        <artifactId>onlinejudge</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>onlinejudge-model</artifactId>
    <name>onlinejudge-model</name>
    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-annotation</artifactId>
        </dependency>
    </dependencies>
</project>
```

`onlinejudge-common/pom.xml` 覆盖为：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.kun</groupId>
        <artifactId>onlinejudge</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>onlinejudge-common</artifactId>
    <name>onlinejudge-common</name>
    <dependencies>
        <dependency>
            <groupId>com.kun</groupId>
            <artifactId>onlinejudge-model</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
        </dependency>
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.redisson</groupId>
            <artifactId>redisson</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

`onlinejudge-service-client/pom.xml` 覆盖为：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.kun</groupId>
        <artifactId>onlinejudge</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>onlinejudge-service-client</artifactId>
    <name>onlinejudge-service-client</name>
    <dependencies>
        <dependency>
            <groupId>com.kun</groupId>
            <artifactId>onlinejudge-common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

`onlinejudge-gateway/pom.xml` 覆盖为（**故意不依赖 common/model/service-client**，保持 WebFlux 纯净）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.kun</groupId>
        <artifactId>onlinejudge</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>onlinejudge-gateway</artifactId>
    <name>onlinejudge-gateway</name>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-loadbalancer</artifactId>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: user/question/submit 三个服务 pom（同为 servlet 服务，采用同一基线）**

基线 `onlinejudge-user-service/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.kun</groupId>
        <artifactId>onlinejudge</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>onlinejudge-user-service</artifactId>
    <name>onlinejudge-user-service</name>
    <dependencies>
        <dependency>
            <groupId>com.kun</groupId>
            <artifactId>onlinejudge-service-client</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-loadbalancer</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

question/submit：复制该 pom 后修改三处——`<artifactId>`/`<name>` 为 `onlinejudge-question-service` / `onlinejudge-submit-service`；并在两文件的 `spring-cloud-starter-loadbalancer` 之后追加 openfeign：

```xml
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>
```

submit 额外在 `lombok` 之前追加 amqp：

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-amqp</artifactId>
        </dependency>
```

- [ ] **Step 4: judge-service 使用纯后台 pom（无 web、有 AMQP、发现注册）**

`onlinejudge-judge-service/pom.xml` 覆盖为：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.kun</groupId>
        <artifactId>onlinejudge</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>onlinejudge-judge-service</artifactId>
    <name>onlinejudge-judge-service</name>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-amqp</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 5: 全量安装验证**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
mvn -DskipTests install 2>&1 | tail -25
```
Expected: `BUILD SUCCESS`。若某模块失败，多为 pom 语法/依赖版本——对照 Global Constraints 修正后重跑。

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "build(foundation): re-parent all modules to aggregate pom, purge SB4 scaffold residue"
```

---

### Task 3: 填充 `onlinejudge-model`（自包含纯数据层）

**Files:**
- Create: `onlinejudge-model/src/main/java/com/kun/onlinejudge/model/request/PageRequest.java`
- Create: `onlinejudge-model/src/main/java/com/kun/onlinejudge/model/request/DeleteRequest.java`
- Move(原封搬入): `model/entity/*.java`、`model/dto/**`、`model/vo/*.java`、`model/enums/*.java`、`model/judge/*.java`、`model/codesandbox/*.java`（源：`D:\Java_project\onlineJudge\src\main\java\com\kun\onlinejudge\model\**`，内容与 package 声明逐字保留）
- Modify: `onlinejudge-model/.../dto/{question/QuestionQueryRequest, questionsubmit/QuestionSubmitQueryRequest, user/UserQueryRequest}.java` 的分页 import

**Interfaces:**
- Produces: `com.kun.onlinejudge.model.request.PageRequest{int current; int pageSize; String sortField; String sortOrder}`、`DeleteRequest{Long id}`；`com.kun.onlinejudge.model.*` 全部实体/DTO/VO/枚举/判题模型。后续 common/service/Feign 均引用此层。

- [ ] **Step 1: 新建两个 request 基类（包 `com.kun.onlinejudge.model.request`）**

`onlinejudge-model/src/main/java/com/kun/onlinejudge/model/request/PageRequest.java`：

```java
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
```

`onlinejudge-model/src/main/java/com/kun/onlinejudge/model/request/DeleteRequest.java`：

```java
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
```

- [ ] **Step 2: 把原 model 全部类搬入（内容逐字保留，含各自 package 声明）**

```bash
SRC="D:/Java_project/onlineJudge/src/main/java/com/kun/onlinejudge/model"
DST="D:/Java_project/onlineJudge-cloud/onlineJudge/onlinejudge-model/src/main/java/com/kun/onlinejudge/model"
mkdir -p "$DST"
cp -r "$SRC/entity" "$SRC/dto" "$SRC/vo" "$SRC/enums" "$SRC/judge" "$SRC/codesandbox" "$DST/"
find "$DST" -name '*.java' | wc -l
```
Expected: **34**（entity=4、dto=14、enums=6、vo=4、judge=4、codesandbox=2）。若 `< 34`，停止并找出遗漏目录。注意不搬 `model/request`（本为空），也**不要**从单体搬 `common/PageRequest|DeleteRequest`（以 Step 1 新建版为准）。

- [ ] **Step 3: 改 3 个 QueryRequest 的分页 import**

对 `dto/question/QuestionQueryRequest.java`、`dto/questionsubmit/QuestionSubmitQueryRequest.java`、`dto/user/UserQueryRequest.java` 各改一行：

```
import com.kun.onlinejudge.common.PageRequest;   →   import com.kun.onlinejudge.model.request.PageRequest;
```

（先 `grep -n "common.PageRequest" "$file"` 定位，再 Edit。）

- [ ] **Step 4: 确认 model 不再引用 common 任何包**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge/onlinejudge-model
grep -rn "com.kun.onlinejudge.common\|com.kun.onlinejudge.constant\|com.kun.onlinejudge.utils\|com.kun.onlinejudge.exception" src/ || echo "CLEAN"
```
Expected: `CLEAN`。若有命中，逐个改到 model 内对应类。

- [ ] **Step 5: 编译验证**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
mvn -pl onlinejudge-model -am install -DskipTests 2>&1 | tail -15
```
Expected: `BUILD SUCCESS`。若报缺 hutool/注解依赖，回到 model pom 补齐版本后重跑。

- [ ] **Step 6: 提交**

```bash
git add onlinejudge-model
git commit -m "feat(foundation): fill self-contained onlinejudge-model (entity/dto/vo/enums/judge/request)"
```

---

### Task 4: 填充 `onlinejudge-common`（servlet 侧基础设施，依赖 model）

**Files:**
- Move(原封搬入): `common/BaseResponse|ErrorCode|ResultUtils.java`、`exception/**`、`constant/CommonConstant|RedisConstant|UserConstant.java`、`utils/DeviceUtils|NetUtils|SqlUtils|SpringContextUtils|JwtUtils|UserContext.java`、`config/CorsConfig|JsonConfig.java`
- Delete(不迁移): `constant/FileConstant.java`、`config/WebMvcConfig|RedissonConfig|MyBatisPlusConfig|CosClientConfig|WxOpenConfig.java`、`aop/**`、`annotation/**`、`manager/**`、`controller/**`、`service/**`、`judgement/**`、`codesandbox/**`、`messagelist/**`（在各自服务子计划迁移或已废弃）
- 源目录：`D:\Java_project\onlineJudge\src\main\java\com\kun\onlinejudge\...`

**Interfaces:**
- Consumes: `onlinejudge-model`（`UserContext`/`JwtUtils` 引用 model 的 `vo.LoginUserVO`/`entity.User`/`dto.user.RefreshTokenResult`）。
- Produces: `common.BaseResponse`/`ErrorCode`/`ResultUtils`、`exception.BusinessException`/`GlobalExceptionHandler`/`ThrowUtils`、`constant.CommonConstant`/`RedisConstant`/`UserConstant`、`utils.*`、`config.CorsConfig`/`JsonConfig`。

- [ ] **Step 1: 搬入保留文件**

```bash
SRC="D:/Java_project/onlineJudge/src/main/java/com/kun/onlinejudge"
DST="D:/Java_project/onlineJudge-cloud/onlineJudge/onlinejudge-common/src/main/java/com/kun/onlinejudge"
mkdir -p "$DST"
cp "$SRC/common/BaseResponse.java" "$SRC/common/ErrorCode.java" "$SRC/common/ResultUtils.java" "$DST/common/"
cp -r "$SRC/exception"  "$DST/"
cp "$SRC/constant/CommonConstant.java" "$SRC/constant/RedisConstant.java" "$SRC/constant/UserConstant.java" "$DST/constant/"
cp "$SRC/utils/DeviceUtils.java" "$SRC/utils/NetUtils.java" "$SRC/utils/SqlUtils.java" \
   "$SRC/utils/SpringContextUtils.java" "$SRC/utils/JwtUtils.java" "$SRC/utils/UserContext.java" "$DST/utils/"
cp "$SRC/config/CorsConfig.java" "$SRC/config/JsonConfig.java" "$DST/config/"
find "$DST" -name '*.java' | wc -l
```
Expected: **17**（common=3、exception=3、constant=3、utils=6、config=2）。

- [ ] **Step 2: 编译验证并处理残差引用**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
mvn -pl onlinejudge-common -am install -DskipTests 2>&1 | tail -20
```
Expected: `BUILD SUCCESS`。常见残差：搬入类仍 import 已迁走的 `common.PageRequest`（改 `model.request.PageRequest`）或未搬入的 `FileConstant`（删引用/常量），修正后重跑。

- [ ] **Step 3: 提交**

```bash
git add onlinejudge-common
git commit -m "feat(foundation): fill onlinejudge-common (result/exception/constants/utils/config)"
```

---

### Task 5: `service-client` 建立两个 Feign 契约接口（编译级）

**Files:**
- Create: `onlinejudge-service-client/src/main/java/com/kun/onlinejudge/serviceclient/UserServiceClient.java`
- Create: `onlinejudge-service-client/src/main/java/com/kun/onlinejudge/serviceclient/QuestionServiceClient.java`

**Interfaces:**
- Consumes: `common.BaseResponse`、`model.vo.UserVO/QuestionVO`。
- Produces（供后续子计划消费）:
  - `UserServiceClient`：`BaseResponse<UserVO> getUserVOById(Long id)`；`BaseResponse<List<UserVO>> listUserVOByIds(List<Long> ids)`
  - `QuestionServiceClient`：`BaseResponse<QuestionVO> getQuestionVOById(Long id)`
- 服务名：`onlinejudge-user-service` / `onlinejudge-question-service`；路径都带 `/api/inner/...`（服务 context-path=/api + `/inner/...` 映射）。

- [ ] **Step 1: 编写 `UserServiceClient`**

```java
package com.kun.onlinejudge.serviceclient;

import com.kun.onlinejudge.common.BaseResponse;
import com.kun.onlinejudge.model.vo.UserVO;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 用户服务内部契约（消费方视角；由 user-service 的 /api/inner/user/** 提供）
 */
@FeignClient(name = "onlinejudge-user-service", contextId = "userServiceClient")
public interface UserServiceClient {

    @GetMapping("/api/inner/user/{id}/vo")
    BaseResponse<UserVO> getUserVOById(@PathVariable("id") Long id);

    @PostMapping("/api/inner/user/list/vo")
    BaseResponse<List<UserVO>> listUserVOByIds(@RequestBody List<Long> ids);
}
```

- [ ] **Step 2: 编写 `QuestionServiceClient`**

```java
package com.kun.onlinejudge.serviceclient;

import com.kun.onlinejudge.common.BaseResponse;
import com.kun.onlinejudge.model.vo.QuestionVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 题目服务内部契约（消费方视角；由 question-service 的 /api/inner/question/** 提供）
 */
@FeignClient(name = "onlinejudge-question-service", contextId = "questionServiceClient")
public interface QuestionServiceClient {

    @GetMapping("/api/inner/question/{id}")
    BaseResponse<QuestionVO> getQuestionVOById(@PathVariable("id") Long id);
}
```

- [ ] **Step 3: 编译验证**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
mvn -pl onlinejudge-service-client -am install -DskipTests 2>&1 | tail -15
```
Expected: `BUILD SUCCESS`。若 `UserVO/QuestionVO` 路径不符，先到 `onlinejudge-model` `find` 确认后修 import。

- [ ] **Step 4: 提交**

```bash
git add onlinejudge-service-client
git commit -m "feat(foundation): add Feign contracts UserServiceClient & QuestionServiceClient"
```

---

### Task 6: 冒烟——最小可运行的 5 模块验证 Nacos / 网关 / Feign / RabbitMQ

本任务会创建**临时占位**的 ping/produce/listen 代码，后续各服务子计划会用真实业务替换/删除。冒烟需要本机可用 **Nacos(8848)** 与 **RabbitMQ(guest/guest)**；不连 DB/Redis。三个 HTTP 服务统一 `context-path: /api`（与单体及最终契约一致）。

**Files:**
- Create（网关）: `onlinejudge-gateway/src/main/java/com/kun/onlinejudge/gateway/OnlinejudgeGatewayApplication.java`、`onlinejudge-gateway/src/main/resources/application.yml`
- Create（user）: `.../userservice/OnlinejudgeUserServiceApplication.java`、`.../userservice/controller/PingController.java`、`onlinejudge-user-service/src/main/resources/application.yml`
- Create（question）: `.../questionservice/OnlinejudgeQuestionServiceApplication.java`、`.../questionservice/controller/PingController.java`、`.../questionservice/feign/SmokeUserPingClient.java`、`onlinejudge-question-service/src/main/resources/application.yml`
- Create（submit）: `.../submitservice/OnlinejudgeSubmitServiceApplication.java`、`.../submitservice/config/RabbitSmokeConfig.java`、`.../submitservice/controller/SmokeSendController.java`、`.../submitservice/controller/PingController.java`、`onlinejudge-submit-service/src/main/resources/application.yml`
- Create（judge）: `.../judgeservice/OnlinejudgeJudgeServiceApplication.java`、`.../judgeservice/config/RabbitSmokeConfig.java`、`.../judgeservice/listener/SmokeListener.java`、`onlinejudge-judge-service/src/main/resources/application.yml`

**Interfaces:**
- Consumes: Nacos 注册中心；gateway `lb://` 各服务（不剥前缀）；question 用 `SmokeUserPingClient`(Feign) 直连 user-service `/api/inner/ping`；submit→judge 经队列 `code-queue`。
- Produces（验收路径）:
  - `curl localhost:8888/api/question/ping` → `question-ok (via user:user-ok)`（网关→question→Feign→user 全链路）
  - `curl -XPOST localhost:8888/api/submit/smoke/send` → judge 日志出现 `[judge-smoke] received ...`；`code-queue` 积压 0

- [ ] **Step 1: 网关启动类 + 配置**

`.../gateway/OnlinejudgeGatewayApplication.java`：
```java
package com.kun.onlinejudge.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OnlinejudgeGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(OnlinejudgeGatewayApplication.class, args);
    }
}
```

`onlinejudge-gateway/src/main/resources/application.yml`：
```yaml
server:
  port: 8888
spring:
  application:
    name: onlinejudge-gateway
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
    gateway:
      discovery:
        locator:
          enabled: false
      routes:
        - id: user-route
          uri: lb://onlinejudge-user-service
          predicates:
            - Path=/api/user/**
        - id: question-route
          uri: lb://onlinejudge-question-service
          predicates:
            - Path=/api/question/**
        - id: submit-route
          uri: lb://onlinejudge-submit-service
          predicates:
            - Path=/api/submit/**
```
说明：网关**不做前缀剥离**，把 `/api/**` 原样转发；服务端用各自 `context-path=/api` 命中内部映射（如 `/api/user/ping` → 服务映射 `/user/ping`）。

- [ ] **Step 2: user-service 启动类 + ping**

`.../userservice/OnlinejudgeUserServiceApplication.java`：
```java
package com.kun.onlinejudge.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OnlinejudgeUserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OnlinejudgeUserServiceApplication.class, args);
    }
}
```

`.../userservice/controller/PingController.java`（同一方法同时供：网关 `/api/user/ping` → context /api + 映射 `/user/ping`；question Feign 直连 `/api/inner/ping` → context /api + 映射 `/inner/ping`）：
```java
package com.kun.onlinejudge.userservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    @GetMapping({"/user/ping", "/inner/ping"})
    public String ping() {
        return "user-ok";
    }
}
```

`onlinejudge-user-service/src/main/resources/application.yml`：
```yaml
server:
  port: 8101
  servlet:
    context-path: /api
spring:
  application:
    name: onlinejudge-user-service
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
```

- [ ] **Step 3: question-service 启动类 + Feign 客户端 + ping**

`.../questionservice/OnlinejudgeQuestionServiceApplication.java`：
```java
package com.kun.onlinejudge.questionservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.kun.onlinejudge.questionservice.feign")
public class OnlinejudgeQuestionServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OnlinejudgeQuestionServiceApplication.class, args);
    }
}
```

`.../questionservice/feign/SmokeUserPingClient.java`（临时占位，验证 Feign+Nacos+LB；后续随真实业务删除）：
```java
package com.kun.onlinejudge.questionservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "onlinejudge-user-service", contextId = "smokeUserPingClient")
public interface SmokeUserPingClient {

    /** 直连 user-service（不带 context-path 的 URL 须显式含 /api 前缀） */
    @GetMapping("/api/inner/ping")
    String ping();
}
```

`.../questionservice/controller/PingController.java`：
```java
package com.kun.onlinejudge.questionservice.controller;

import com.kun.onlinejudge.questionservice.feign.SmokeUserPingClient;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    @Resource
    private SmokeUserPingClient smokeUserPingClient;

    @GetMapping("/question/ping")
    public String ping() {
        String viaUser = smokeUserPingClient.ping();
        return "question-ok (via user:" + viaUser + ")";
    }
}
```

`onlinejudge-question-service/src/main/resources/application.yml`：
```yaml
server:
  port: 8102
  servlet:
    context-path: /api
spring:
  application:
    name: onlinejudge-question-service
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
```

- [ ] **Step 4: submit-service 启动类 + Rabbit 声明 + 发送端点**

`.../submitservice/OnlinejudgeSubmitServiceApplication.java`：
```java
package com.kun.onlinejudge.submitservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OnlinejudgeSubmitServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OnlinejudgeSubmitServiceApplication.class, args);
    }
}
```

`.../submitservice/config/RabbitSmokeConfig.java`：
```java
package com.kun.onlinejudge.submitservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitSmokeConfig {

    public static final String EXCHANGE = "code-exchange";
    public static final String QUEUE = "code-queue";
    public static final String ROUTING_KEY = "code.routing.key";

    @Bean
    public DirectExchange codeExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue codeQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding codeBinding(DirectExchange codeExchange, Queue codeQueue) {
        return BindingBuilder.bind(codeQueue).to(codeExchange).with(ROUTING_KEY);
    }
}
```

`.../submitservice/controller/SmokeSendController.java`：
```java
package com.kun.onlinejudge.submitservice.controller;

import com.kun.onlinejudge.submitservice.config.RabbitSmokeConfig;
import javax.annotation.Resource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SmokeSendController {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @GetMapping("/submit/smoke/send")
    public String send() {
        String msg = "smoke-" + System.currentTimeMillis();
        rabbitTemplate.convertAndSend(RabbitSmokeConfig.EXCHANGE, RabbitSmokeConfig.ROUTING_KEY, msg);
        return "sent:" + msg;
    }
}
```

`.../submitservice/controller/PingController.java`：
```java
package com.kun.onlinejudge.submitservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    @GetMapping("/submit/ping")
    public String ping() {
        return "submit-ok";
    }
}
```

`onlinejudge-submit-service/src/main/resources/application.yml`：
```yaml
server:
  port: 8103
  servlet:
    context-path: /api
spring:
  application:
    name: onlinejudge-submit-service
  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: guest
    password: guest
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
```

- [ ] **Step 5: judge-service 启动类 + Rabbit 声明 + 监听**

`.../judgeservice/OnlinejudgeJudgeServiceApplication.java`：
```java
package com.kun.onlinejudge.judgeservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 判题服务：纯后台消费者，pom 无 web 依赖，SpringApplication 自动以非 web 上下文启动。
 */
@SpringBootApplication
public class OnlinejudgeJudgeServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OnlinejudgeJudgeServiceApplication.class, args);
    }
}
```

`.../judgeservice/config/RabbitSmokeConfig.java`：与 Step 4 的 `RabbitSmokeConfig` **类体完全一致**，仅 `package com.kun.onlinejudge.judgeservice.config;`。

`.../judgeservice/listener/SmokeListener.java`：
```java
package com.kun.onlinejudge.judgeservice.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SmokeListener {

    @RabbitListener(queues = "code-queue")
    public void onMessage(String message) {
        log.info("[judge-smoke] received from code-queue: {}", message);
    }
}
```

`onlinejudge-judge-service/src/main/resources/application.yml`：
```yaml
spring:
  application:
    name: onlinejudge-judge-service
  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: guest
    password: guest
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
```

- [ ] **Step 6: 整体编译**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
mvn -DskipTests install 2>&1 | tail -15
```
Expected: `BUILD SUCCESS`。

- [ ] **Step 7: 前置检查（Nacos、RabbitMQ 必须可用）**

```bash
curl -s http://127.0.0.1:8848/nacos/ >/dev/null && echo "nacos ok" || echo "NACOS DOWN"
curl -s http://127.0.0.1:15672/ >/dev/null && echo "rabbitmq mgmt ok" || echo "rabbit NOT reachable on 15672 (需先启动 rabbitmq)"
```
Expected: 两行均为 ok；否则先在本机启动 Nacos(2.x 单机) 与 RabbitMQ 再继续。

- [ ] **Step 8: 分别启动 5 个模块（5 个终端）**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
mvn -pl onlinejudge-gateway          spring-boot:run
mvn -pl onlinejudge-user-service     spring-boot:run
mvn -pl onlinejudge-question-service spring-boot:run
mvn -pl onlinejudge-submit-service   spring-boot:run
mvn -pl onlinejudge-judge-service    spring-boot:run
```
Expected: 各终端出现 `Started Onlinejudge*Application`；`http://127.0.0.1:8848/nacos/#/serviceManagement` 能看到 5 个服务名注册。

- [ ] **Step 9: 验收 1——网关→question→Feign→user**

```bash
curl -s http://127.0.0.1:8888/api/question/ping
```
Expected: `question-ok (via user:user-ok)`（证明 gateway 路由 + Nacos LB + question→user 的 Feign 全通）。

- [ ] **Step 10: 验收 2——Rabbit 发送并被 judge 消费**

```bash
curl -s http://127.0.0.1:8888/api/submit/smoke/send
```
Expected: 返回 `sent:...`；judge 终端日志出现 `[judge-smoke] received from code-queue: smoke-...`；Rabbit 管理页 `code-queue` 积压为 0。

- [ ] **Step 11: 提交冒烟占位代码**

```bash
git add -A
git commit -m "feat(foundation): add minimal runnable apps proving nacos/gateway/feign/rabbit on SCA2021 stack"
```

---

## Self-Review 结论（作者已自查）

- **设计覆盖**：本计划覆盖设计文档 §9 Phase 0–2；服务迁移（user/question/submit/judge）、网关统一鉴权、端到端回归属后续子计划。
- **两库循环**：通过 `PageRequest/DeleteRequest → com.kun.onlinejudge.model.request` + PageRequest 内联 `"ascend"` + 3 个 QueryRequest 改 import 消除；Task 3 Step 4 grep 兜底。
- **网关纯净性**：gateway 不依赖 common/model/service-client（WebFlux 冲突）；Task 2 Step 2 网关 pom 已体现。
- **路由一致性**：统一 `context-path=/api` + 网关不剥前缀 + Feign URL 显式 `/api/...`，与设计 §③ 及 service-client 的 `/api/inner/**` 契约一致（Task 6 Step 1 已同步）。
- **占位扫描**：无 TBD/TODO；冒烟中的 `SmokeUserPingClient`/`RabbitSmokeConfig` 为显式声明的临时占位并注明后续删除，属计划内。
- **类型一致性**：Feign 契约方法与后续 `/api/inner/**` 路径、服务名、`BaseResponse<VO>` 类型在 Task 5 与 Global Constraints 一致。

---

## Epilogue — Forward constraints for next sub-plans (from final review 2026-09-09)

Foundation 终审（可合并，无 Critical/must-fix）给出 4 条须由后续服务迁移子计划继承的协调约束：

1. **BaseResponse/ErrorCode 归属前置拍板**：网关(WebFlux)不能依赖 common（common 依赖 starter-web/servlet）。网关统一鉴权子计划必须先把 `common.BaseResponse`/`ErrorCode` 这两个无 servlet 依赖的纯类**下沉到 model**（网关可依赖 model 而无冲突），或在网关内小复刻，以产出同构错误体（设计 §6.2.4）。在 gateway 子计划开工前决定。
2. **Rabbit 常量收进 common**：`code-exchange`/`code-queue`/`code.routing.key`（现 submit/judge 各复刻一份 + SmokeListener 再硬编码队列名）须在真实迁移时收为 common 的 `RabbitConstant`，producer/consumer/listener 同源引用。
3. **Feign 启用与冒烟占位清理**：`@EnableFeignClients` 需覆盖 `com.kun.onlinejudge.serviceclient`（或 `clients={...}`）；submit-service 迁移时需新增 `@EnableFeignClients`。冒烟占位件（`SmokeUserPingClient`、各 `PingController`、`SmokeSendController`、两处 `RabbitSmokeConfig`）的**删除**是每个服务迁移任务的显式第一步——谨防 `RabbitSmokeConfig` 的 bean 名（`codeExchange`/`codeQueue`）与真实 Rabbit 声明重名导致启动冲突。
4. **common 禁整包扫描**：四服务根包与 common 是兄弟包，须用受限 `@Import`/窄扫描引入 `GlobalExceptionHandler`/`CorsConfig`/`JsonConfig` 等；禁止 `@ComponentScan("com.kun.onlinejudge")`（会把 `JwtUtils`(需 `jwt.secret`) 与兄弟服务控制器串扫进来）。

其它 carry-over：冒烟占位路径 `/ping`、`/inner/ping`、`/submit/smoke/send` 与单体真实映射（`/user`、`/question`、`/question_submit`）无冲突，迁移时整体删除即可。
