package com.oms.integration.provider;

import com.oms.integration.dto.IntegrationDtos.ExternalOrderPullRequest;
import com.oms.integration.dto.IntegrationDtos.SyncAfterSalesRequest;
import com.oms.integration.dto.IntegrationDtos.SyncShipmentRequest;
import com.oms.integration.dto.IntegrationDtos.SyncStockRequest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 第三方平台 HTTP 适配器：接入平台开放平台网关，按平台标准字段映射后转发。
 */
@Component
public class HttpPlatformProvider implements PlatformProvider {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${oms.integration.platform.mock-only:true}")
    private boolean mockOnly;

    @Value("${oms.integration.platform.base-url:}")
    private String baseUrl;

    @Value("${oms.integration.platform.api-key:}")
    private String apiKey;

    @Override
    public boolean isConfigured() {
        return !mockOnly && hasText(baseUrl);
    }

    @Override
    public void pullOrder(ExternalOrderPullRequest request) {
        post("/orders/pull", request);
    }

    @Override
    public void syncShipment(SyncShipmentRequest request) {
        post("/shipments", request);
    }

    @Override
    public void syncStock(SyncStockRequest request) {
        post("/stocks", request);
    }

    @Override
    public void syncAfterSales(SyncAfterSalesRequest request) {
        post("/after-sales", request);
    }

    private void post(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (hasText(apiKey)) {
            headers.setBearerAuth(apiKey);
        }
        restTemplate.postForEntity(baseUrl + path, new HttpEntity<>(body, headers), Void.class);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
