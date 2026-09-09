package com.kun.onlinejudge.submitservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {
        "com.kun.onlinejudge.submitservice",
        "com.kun.onlinejudge.annotation",
        "com.kun.onlinejudge.security",
        "com.kun.onlinejudge.exception",
        "com.kun.onlinejudge.config"
})
@MapperScan("com.kun.onlinejudge.submitservice.mapper")
@EnableFeignClients(basePackages = "com.kun.onlinejudge.serviceclient")
public class OnlinejudgeSubmitServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OnlinejudgeSubmitServiceApplication.class, args);
    }
}
