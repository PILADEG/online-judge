package com.kun.onlinejudge.userservice.config;

import com.kun.onlinejudge.model.auth.JwtUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Bean
    public JwtUtils jwtUtils(@Value("${jwt.secret}") String secret,
                             @Value("${jwt.access-token-expire}") long access,
                             @Value("${jwt.refresh-token-expire}") long refresh) {
        return new JwtUtils(secret, access, refresh);
    }
}
