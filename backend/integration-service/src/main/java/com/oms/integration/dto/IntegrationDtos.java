package com.oms.integration.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class IntegrationDtos {

    private IntegrationDtos() {
    }

    public record ExternalOrderPullRequest(
            String platform,
            String platformOrderNo,
            String orderNo,
            String rawData) {
    }

    public record ExternalOrderMappingResponse(
            Long id,
            String platform,
            String platformOrderNo,
            String orderNo,
            Integer status,
            String errorMessage,
            LocalDateTime createdAt) {
    }

    public record SyncShipmentRequest(
            String platform,
            String platformOrderNo,
            String orderNo,
            String trackingNo,
            String carrier) {
    }

    public record SyncStockRequest(
            String platform,
            String skuNo,
            Integer quantity,
            String warehouseCode) {
    }

    public record SyncAfterSalesRequest(
            String platform,
            String platformOrderNo,
            String returnNo,
            String status) {
    }

    public record LogisticsCallbackRequest(
            String trackingNo,
            String carrier,
            String status,
            String trace,
            String orderNo) {
    }

    public record LogisticsResponse(
            Long id,
            String orderNo,
            String trackingNo,
            String carrier,
            String status,
            List<String> trace,
            LocalDateTime updatedAt) {
    }

    public record TemplateRequest(
            String code,
            String name,
            String channel,
            String scene,
            String titleTemplate,
            String contentTemplate,
            Integer status) {
    }

    public record TemplateResponse(
            Long id,
            String code,
            String name,
            String channel,
            String scene,
            String titleTemplate,
            String contentTemplate,
            Integer status,
            LocalDateTime updatedAt) {
    }

    public record SendRequest(
            String channel,
            String scene,
            String receiver,
            String title,
            String content) {
    }

    public record MessageResponse(
            Long id,
            String messageNo,
            String channel,
            String scene,
            String receiver,
            String title,
            String content,
            Integer status,
            Integer retryCount,
            String errorMessage,
            LocalDateTime sentAt,
            LocalDateTime createdAt) {
    }
}
