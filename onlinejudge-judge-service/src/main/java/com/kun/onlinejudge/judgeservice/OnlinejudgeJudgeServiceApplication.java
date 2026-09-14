package com.kun.onlinejudge.judgeservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.kun.onlinejudge.judgeservice.mapper")
@EnableScheduling
public class OnlinejudgeJudgeServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OnlinejudgeJudgeServiceApplication.class, args);
    }
}
