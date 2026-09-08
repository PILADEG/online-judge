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
