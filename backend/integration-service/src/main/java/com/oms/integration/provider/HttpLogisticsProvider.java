package com.oms.integration.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 真实物流承运商 HTTP 适配器：通过 base-url 查询轨迹，响应为 JSON，
 * {@code data.traces} 或 {@code traces} 为轨迹文本数组。
 */
@Component
public class HttpLogisticsProvider implements LogisticsProvider {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${oms.integration.logistics.mock-only:true}")
    private boolean mockOnly;

    @Value("${oms.integration.logistics.base-url:}")
    private String baseUrl;

    @Value("${oms.integration.logistics.api-key:}")
    private String apiKey;

    @Override
    public boolean isConfigured() {
        return !mockOnly && hasText(baseUrl);
    }

    @Override
    public List<String> query(String trackingNo, String carrier) {
        if (!isConfigured()) {
            return List.of();
        }
        try {
            String url = baseUrl
                    + "?trackingNo=" + encode(trackingNo)
                    + "&carrier=" + encode(carrier);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json");
            if (hasText(apiKey)) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> response =
                    httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("物流查询失败 status=" + response.statusCode());
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode traces = root.has("data") && root.path("data").has("traces")
                    ? root.path("data").path("traces")
                    : root.path("traces");
            List<String> result = new ArrayList<>();
            traces.elements().forEachRemaining(node -> result.add(node.asText()));
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("调用物流承运商失败: " + ex.getMessage(), ex);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
