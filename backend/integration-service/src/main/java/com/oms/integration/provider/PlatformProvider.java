package com.oms.integration.provider;

import com.oms.integration.dto.IntegrationDtos.ExternalOrderPullRequest;
import com.oms.integration.dto.IntegrationDtos.SyncAfterSalesRequest;
import com.oms.integration.dto.IntegrationDtos.SyncShipmentRequest;
import com.oms.integration.dto.IntegrationDtos.SyncStockRequest;

/**
 * 第三方电商平台（天猫/京东等）真实适配抽象。平台权限就绪后替换为官方 API 实现。
 */
public interface PlatformProvider {

    boolean isConfigured();

    void pullOrder(ExternalOrderPullRequest request);

    void syncShipment(SyncShipmentRequest request);

    void syncStock(SyncStockRequest request);

    void syncAfterSales(SyncAfterSalesRequest request);
}
