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
