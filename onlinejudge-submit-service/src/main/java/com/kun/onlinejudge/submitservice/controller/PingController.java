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
