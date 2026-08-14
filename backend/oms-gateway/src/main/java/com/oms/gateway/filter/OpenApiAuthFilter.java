package com.oms.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.Result;
import com.oms.gateway.config.OpenApiProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 商城开放 API 签名过滤器。
 *
 * <p>对接商城（如自营商城、分销平台）通过 {@code /api/v1/open/**} 调用 OMS。
 * 签名方案（HMAC-SHA256）：
 *
 * <pre>
 * stringToSign = METHOD + "\n" + PATH + "\n" + TIMESTAMP + "\n" + NONCE + "\n" + SHA256_HEX(BODY)
 * signature    = Hex(HMAC_SHA256(secret, stringToSign))
 * </pre>
 *
 * <p>请求头：{@code X-App-Id}、{@code X-Timestamp}（Unix 秒）、{@code X-Nonce}（随机串）、
 * {@code X-Sign}（签名）。时间戳窗口 ±5 分钟防重放，nonce 在窗口内一次性使用。
 *
 * <p>校验通过后向下游透传 {@code X-App-Id} 与 {@code X-Merchant-Id}（由 appId 映射）。
 * 当前 nonce 缓存为进程内实现，多实例生产环境应替换为 Redis 统一缓存。
 */
@Component
public class OpenApiAuthFilter implements GlobalFilter, Ordered {

    public static final String PATH_PREFIX = "/api/v1/open/";
    public static final String HEADER_APP_ID = "X-App-Id";
    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_NONCE = "X-Nonce";
    public static final String HEADER_SIGN = "X-Sign";
    public static final String HEADER_MERCHANT_ID = "X-Merchant-Id";

    private static final int MAX_NONCE_CACHE = 10_000;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenApiProperties properties;
    private final Map<String, Long> nonceCache = new ConcurrentHashMap<>();

    public OpenApiAuthFilter(OpenApiProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        if (!path.startsWith(PATH_PREFIX)) {
            return chain.filter(exchange);
        }
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        HttpHeaders headers = request.getHeaders();
        String appId = headers.getFirst(HEADER_APP_ID);
        String timestamp = headers.getFirst(HEADER_TIMESTAMP);
        String nonce = headers.getFirst(HEADER_NONCE);
        String sign = headers.getFirst(HEADER_SIGN);

        if (appId == null || timestamp == null || nonce == null || sign == null) {
            return reject(exchange, "缺少开放 API 签名头");
        }
        OpenApiProperties.Client client = properties.getClients().get(appId);
        if (client == null || !client.isEnabled()) {
            return reject(exchange, "未授权的 AppId");
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            return reject(exchange, "X-Timestamp 非法");
        }
        long now = System.currentTimeMillis() / 1000;
        if (Math.abs(now - ts) > properties.getTimestampWindowSeconds()) {
            return reject(exchange, "请求已过期（时间戳窗口外）");
        }
        if (!isNonceFresh(appId, nonce)) {
            return reject(exchange, "重复请求（nonce 已使用）");
        }

        boolean hasBody = request.getMethod() == HttpMethod.POST
                || request.getMethod() == HttpMethod.PUT
                || request.getMethod() == HttpMethod.PATCH;
        if (!hasBody) {
            String rawPath = request.getURI().getRawPath();
            if (verify(appId, client.getSecret(), request.getMethod().name(), rawPath,
                    timestamp, nonce, "", sign)) {
                return forward(exchange, chain, appId, client);
            }
            return reject(exchange, "签名校验失败");
        }

        return DataBufferUtils.join(request.getBody())
                .map(buffer -> {
                    byte[] body = new byte[buffer.readableByteCount()];
                    buffer.read(body);
                    DataBufferUtils.release(buffer);
                    return body;
                })
                .defaultIfEmpty(new byte[0])
                .flatMap(body -> {
                    String rawPath = request.getURI().getRawPath();
                    if (!verify(appId, client.getSecret(), request.getMethod().name(), rawPath,
                            timestamp, nonce, body, sign)) {
                        return reject(exchange, "签名校验失败");
                    }
                    // 重新包装请求体，避免下游读不到 body
                    byte[] bodyCopy = body;
                    ServerHttpRequest mutated = new ServerHttpRequestDecorator(request) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            DataBuffer bodyBuffer = exchange.getResponse().bufferFactory().wrap(bodyCopy);
                            return Flux.just(bodyBuffer);
                        }
                    };
                    ServerHttpRequest forwarded = mutated.mutate()
                            .header(HEADER_APP_ID, appId)
                            .header(HEADER_MERCHANT_ID, String.valueOf(client.getMerchantId()))
                            .build();
                    return chain.filter(exchange.mutate().request(forwarded).build());
                });
    }

    @Override
    public int getOrder() {
        return -200;
    }

    private Mono<Void> forward(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            String appId,
            OpenApiProperties.Client client) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(HEADER_APP_ID, appId)
                .header(HEADER_MERCHANT_ID, String.valueOf(client.getMerchantId()))
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean isNonceFresh(String appId, String nonce) {
        if (nonceCache.size() > MAX_NONCE_CACHE) {
            long now = System.currentTimeMillis() / 1000;
            nonceCache.entrySet().removeIf(entry -> entry.getValue() < now);
        }
        String key = appId + ":" + nonce;
        long expiresAt = System.currentTimeMillis() / 1000 + properties.getTimestampWindowSeconds() * 2L;
        Long previous = nonceCache.putIfAbsent(key, expiresAt);
        return previous == null;
    }

    private boolean verify(
            String appId,
            String secret,
            String method,
            String path,
            String timestamp,
            String nonce,
            byte[] body,
            String expectedSign) {
        try {
            String stringToSign = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + sha256Hex(body);
            return MessageDigest.isEqual(
                    hmacSha256Hex(secret, stringToSign).getBytes(StandardCharsets.UTF_8),
                    expectedSign.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean verify(
            String appId,
            String secret,
            String method,
            String path,
            String timestamp,
            String nonce,
            String body,
            String expectedSign) {
        return verify(appId, secret, method, path, timestamp, nonce, body.getBytes(StandardCharsets.UTF_8), expectedSign);
    }

    private String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String hmacSha256Hex(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = writeBody(Result.fail(ErrorCode.UNAUTHORIZED.getCode(), "开放 API 校验失败: " + message));
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
