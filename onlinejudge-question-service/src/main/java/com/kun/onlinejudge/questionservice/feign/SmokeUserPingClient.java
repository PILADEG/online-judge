package com.kun.onlinejudge.questionservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "onlinejudge-user-service", contextId = "smokeUserPingClient")
public interface SmokeUserPingClient {

    /** 直连 user-service（不带 context-path 的 URL 须显式含 /api 前缀） */
    @GetMapping("/api/inner/ping")
    String ping();
}
