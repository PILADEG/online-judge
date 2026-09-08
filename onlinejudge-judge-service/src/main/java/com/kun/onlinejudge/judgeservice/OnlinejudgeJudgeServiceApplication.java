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
