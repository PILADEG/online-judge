package com.kun.onlinejudge.questionservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {
        "com.kun.onlinejudge.questionservice",
        "com.kun.onlinejudge.annotation",
        "com.kun.onlinejudge.security",
        "com.kun.onlinejudge.exception",
        "com.kun.onlinejudge.config"
})
@MapperScan("com.kun.onlinejudge.questionservice.mapper")
@EnableFeignClients(basePackages = "com.kun.onlinejudge.serviceclient")
public class OnlinejudgeQuestionServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OnlinejudgeQuestionServiceApplication.class, args);
    }
}
