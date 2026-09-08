package com.kun.onlinejudge.userservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.kun.onlinejudge.userservice",
        "com.kun.onlinejudge.annotation",
        "com.kun.onlinejudge.security",
        "com.kun.onlinejudge.exception",
        "com.kun.onlinejudge.config"
})
@MapperScan("com.kun.onlinejudge.userservice.mapper")
public class OnlinejudgeUserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OnlinejudgeUserServiceApplication.class, args);
    }
}
