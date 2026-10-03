package com.kun.onlinejudge.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.onlinejudge.model.result.BaseResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public final class AuthResultJson {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final byte[] FALLBACK_BYTES =
            "{\"code\":50000,\"data\":null,\"message\":\"响应序列化失败\"}".getBytes(StandardCharsets.UTF_8);

    private AuthResultJson() {
    }

    public static Mono<Void> write(ServerWebExchange exchange, BaseResponse<?> body) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        byte[] bytes = toBytes(body);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private static byte[] toBytes(BaseResponse<?> body) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            return FALLBACK_BYTES;
        }
    }
}
