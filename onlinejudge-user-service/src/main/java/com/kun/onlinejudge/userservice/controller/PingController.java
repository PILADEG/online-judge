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
