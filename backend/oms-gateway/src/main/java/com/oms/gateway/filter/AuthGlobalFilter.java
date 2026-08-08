package com.oms.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.Result;
import com.oms.common.core.security.JwtClaims;
import com.oms.common.core.security.JwtUtil;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 全局认证过滤器：校验 JWT 并将用户上下文透传到下游服务。
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITELIST = List.of(
            "/api/v1/auth/login",
            "/api/v1/merchants/register",
            "/api/v1/payment-callbacks/",
            "/actuator");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${oms.security.jwt-secret}")
    private String jwtSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        try {
            JwtClaims claims = JwtUtil.parseToken(jwtSecret, authorization.substring(7));
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header("X-User-Id", String.valueOf(claims.userId()))
                    .header("X-Username", claims.username())
                    .header("X-User-Type", String.valueOf(claims.userType()))
                    .build();
            if (claims.merchantId() != null) {
                mutated = mutated.mutate()
                        .header("X-Merchant-Id", String.valueOf(claims.merchantId()))
                        .build();
            }
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (JwtException | IllegalArgumentException ex) {
            return unauthorized(exchange);
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isWhitelisted(String path) {
        return WHITELIST.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = writeBody(Result.fail(ErrorCode.UNAUTHORIZED));
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private byte[] writeBody(Result<Void> result) {
        try {
            return objectMapper.writeValueAsBytes(result);
        } catch (Exception ex) {
            return "{\"code\":401,\"message\":\"未认证\"}".getBytes(StandardCharsets.UTF_8);
        }
    }
}
