package com.oms.integration.jtexpress;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.integration.jtexpress.JtExpressDtos.CancelResponse;
import com.oms.integration.jtexpress.JtExpressDtos.ConnoteResponse;
import com.oms.integration.jtexpress.JtExpressDtos.ConnectivityResponse;
import com.oms.integration.jtexpress.JtExpressDtos.CreateShipmentRequest;
import com.oms.integration.jtexpress.JtExpressDtos.CreateShipmentResponse;
import com.oms.integration.jtexpress.JtExpressDtos.TrackingResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JtExpressClient {

    private final JtExpressProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private volatile String accessToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    @Autowired
    public JtExpressClient(JtExpressProperties properties, ObjectMapper mapper) {
        this(properties, mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    JtExpressClient(JtExpressProperties properties, ObjectMapper mapper, HttpClient httpClient) {
        this.properties = properties;
        this.mapper = mapper;
        this.httpClient = httpClient;
    }

    public CreateShipmentResponse createShipment(CreateShipmentRequest input) {
        requireConfigured();
        CreateShipmentRequest request = new CreateShipmentRequest(
                input.referenceNumber(),
                hasText(input.serviceCode()) ? input.serviceCode() : properties.getServiceCode(),
                hasText(input.merchantCode()) ? input.merchantCode() : properties.getMerchantCode(),
                hasText(input.orderSource()) ? input.orderSource() : "OMS",
                input.consigneeDetails(),
                input.itemDetails());
        CreateShipmentResponse response =
                sendJson("POST", "/api/gateway/v1/deliveries", request, CreateShipmentResponse.class);
        if (response == null || !hasText(response.trackingId())) {
            throw new IllegalStateException("J&T Express 创建运单响应缺少 tracking_id");
        }
        return response;
    }

    public TrackingResponse track(String id) {
        requireConfigured();
        return sendJson("GET", "/api/gateway/v1/track/" + path(id), null, TrackingResponse.class);
    }

    public CancelResponse cancel(String id, String reason) {
        requireConfigured();
        Map<String, Object> request = Map.of(
                "type", "CANCEL",
                "data", Map.of("ids", List.of(id), "reason", reason == null ? "" : reason));
        return sendJson("POST", "/api/gateway/v1/deliveries/operation", request, CancelResponse.class);
    }

    public ConnoteResponse connote(String id, String style) {
        requireConfigured();
        String normalizedStyle = "a4".equalsIgnoreCase(style) ? "a4" : "a6";
        return sendJson(
                "GET",
                "/api/gateway/v1/deliveries/connote/" + path(id) + "/" + normalizedStyle,
                null,
                ConnoteResponse.class);
    }

    public ConnectivityResponse verifyConnectivity(String serviceCode) {
        requireConfigured();
        String normalizedServiceCode = hasText(serviceCode) ? serviceCode : "PUDO";
        JsonNode response = sendJson(
                "GET",
                "/api/gateway/v1/distribution-points?service_code=" + path(normalizedServiceCode),
                null,
                JsonNode.class);
        JsonNode points = response != null && response.isArray() ? response : response == null ? null : response.path("data");
        int count = points != null && points.isArray() ? points.size() : 0;
        return new ConnectivityResponse(true, normalizedServiceCode, count);
    }

    private <T> T sendJson(String method, String path, Object body, Class<T> type) {
        try {
            return sendJsonOnce(method, path, body, type);
        } catch (JtUnauthorizedException ex) {
            invalidateToken();
            return sendJsonOnce(method, path, body, type);
        }
    }

    private <T> T sendJsonOnce(String method, String path, Object body, Class<T> type) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("Authorization", "JWT " + accessToken());
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new JtUnauthorizedException();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "J&T Express API 请求失败 status=" + response.statusCode() + " body=" + safeBody(response.body()));
            }
            return mapper.readValue(response.body(), type);
        } catch (JtUnauthorizedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("调用 J&T Express API 失败: " + ex.getMessage(), ex);
        }
    }

    private String accessToken() {
        String cached = accessToken;
        if (hasText(cached) && Instant.now().isBefore(tokenExpiresAt)) {
            return cached;
        }
        synchronized (this) {
            if (hasText(accessToken) && Instant.now().isBefore(tokenExpiresAt)) {
                return accessToken;
            }
            accessToken = login();
            tokenExpiresAt = Instant.now().plus(Duration.ofDays(6).plusHours(23));
            return accessToken;
        }
    }

    private String login() {
        String credentials = properties.getEmail() + ":" + properties.getPassword();
        String basic = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/api/gateway/v1/auth/login"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Basic " + basic)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        IOException lastIoException = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("登录失败 status=" + response.statusCode());
                }
                String token = extractTokenResponse(response.body());
                if (!hasText(token)) {
                    throw new IllegalStateException("登录响应中没有 access token");
                }
                return token;
            } catch (IOException ex) {
                lastIoException = ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("J&T Express 登录被中断", ex);
            }
        }
        throw new IllegalStateException(
                "J&T Express 登录失败: " + lastIoException.getMessage(), lastIoException);
    }

    private static String extractToken(JsonNode root) {
        if (root == null) {
            return null;
        }
        if (root.isTextual()) {
            return root.asText();
        }
        for (String field : List.of("access_token", "accessToken", "token")) {
            if (root.path(field).isTextual()) {
                return root.path(field).asText();
            }
        }
        JsonNode data = root.path("data");
        return data.isMissingNode() ? null : extractToken(data);
    }

    private String extractTokenResponse(String body) {
        try {
            return extractToken(mapper.readTree(body));
        } catch (Exception ignored) {
            String raw = body == null ? null : body.trim();
            return hasText(raw) && !raw.startsWith("{") && !raw.startsWith("[") ? raw : null;
        }
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new IllegalStateException(
                    "J&T Express 尚未启用或账号未配置，请检查 OMS_JTEXPRESS_ENABLED/EMAIL/PASSWORD");
        }
    }

    private void invalidateToken() {
        accessToken = null;
        tokenExpiresAt = Instant.EPOCH;
    }

    private static String path(String value) {
        if (!hasText(value)) {
            throw new IllegalArgumentException("J&T tracking id/reference number 不能为空");
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String safeBody(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 1000 ? body : body.substring(0, 1000);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class JtUnauthorizedException extends RuntimeException {
    }
}
