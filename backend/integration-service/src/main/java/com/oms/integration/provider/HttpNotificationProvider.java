package com.oms.integration.provider;

import com.oms.integration.entity.NotificationMessage;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 真实通知渠道 HTTP 适配器：短信/邮件/微信模板由外部网关或服务商 API 提供。
 * 通过 {@code oms.integration.notification.*} 配置接入，返回 2xx 视为发送成功。
 */
@Component
@ConditionalOnProperty(
        name = "oms.integration.notification.provider",
        havingValue = "http")
public class HttpNotificationProvider implements NotificationProvider {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${oms.integration.notification.mock-only:true}")
    private boolean mockOnly;

    @Value("${oms.integration.notification.base-url:}")
    private String baseUrl;

    @Value("${oms.integration.notification.api-key:}")
    private String apiKey;

    @Override
    public boolean supports(String channel) {
        return true;
    }

    @Override
    public boolean isConfigured() {
        return !mockOnly && hasText(baseUrl);
    }

    @Override
    public void send(NotificationMessage message) {
        if (!isConfigured()) {
            throw new IllegalStateException("真实通知渠道未配置");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", message.getChannel());
        body.put("scene", message.getScene());
        body.put("receiver", message.getReceiver());
        body.put("title", message.getTitle());
        body.put("content", message.getContent());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (hasText(apiKey)) {
            headers.setBearerAuth(apiKey);
        }
        restTemplate.postForEntity(baseUrl, new HttpEntity<>(body, headers), Void.class);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
