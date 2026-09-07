package com.oms.integration.provider;

import com.oms.integration.dto.IntegrationDtos.ExternalOrderPullRequest;
import com.oms.integration.dto.IntegrationDtos.SyncAfterSalesRequest;
import com.oms.integration.dto.IntegrationDtos.SyncShipmentRequest;
import com.oms.integration.dto.IntegrationDtos.SyncStockRequest;
import org.springframework.stereotype.Component;

@Component
public class MockPlatformProvider implements PlatformProvider {

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public void pullOrder(ExternalOrderPullRequest request) {
        // mock：保留本地演示行为
    }

    @Override
    public void syncShipment(SyncShipmentRequest request) {
        // mock
    }

    @Override
    public void syncStock(SyncStockRequest request) {
        // mock
    }

    @Override
    public void syncAfterSales(SyncAfterSalesRequest request) {
        // mock
    }
}
