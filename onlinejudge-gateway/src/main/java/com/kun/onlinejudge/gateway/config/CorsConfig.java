package com.kun.onlinejudge.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * 网关统一跨域配置（WebFlux）。
 *
 * <p>为什么放在网关、以及为什么用 {@link CorsWebFilter} 而不是 yml 的
 * {@code spring.cloud.gateway.globalcors}：
 * <ul>
 *   <li>网关是唯一对外入口，跨域只需在此处理一处；下游 servlet 服务不再各自配置
 *       （原来的 common {@code CorsConfig} 已移除，避免同一个响应出现两份
 *       {@code Access-Control-Allow-Origin} 反而被浏览器判为非法）。</li>
 *   <li>{@code CorsWebFilter} 是最外层的 WebFilter，能覆盖**网关自己产生的响应**——
 *       鉴权失败（40100/40101/40102）、系统错误（50000）、路由不匹配（404）等。
 *       若用 yml 的 globalcors，这些响应走不到 CORS 处理，前端只会看到浏览器的跨域报错、
 *       拿不到 body 里的 code。</li>
 *   <li>预检（OPTIONS + Origin）由本过滤器直接应答，不再转发到下游。</li>
 * </ul>
 *
 * <p>⚠️ {@code allowCredentials(true)} 时不能使用 {@code allowedOrigins("*")}，必须用
 * {@code allowedOriginPatterns}；上生产应把 {@code "*"} 收紧为具体前端域名。
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许携带凭证（本项目用 Bearer token，凭证主要影响 Cookie/Authorization 透传语义）
        config.setAllowCredentials(true);
        // 放行来源：开发期放开；生产替换为具体域名，如 https://oj.example.com
        config.addAllowedOriginPattern("*");
        // 与前端使用的 HTTP 方法保持一致
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("OPTIONS");
        config.addAllowedHeader("*");
        // ⚠️ 必须暴露：续签时新 token 放在响应头 X-Access-Token / X-Refresh-Token 里，
        //    不暴露的话浏览器端读不到，access 过期后无法自动续签
        config.addExposedHeader("*");
        // 预检结果缓存时间（秒）
        config.setMaxAge(1800L);

        // WebFlux 的 UrlBasedCorsConfigurationSource 需要 PathPatternParser（非 Servlet 版）
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(new PathPatternParser());
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
