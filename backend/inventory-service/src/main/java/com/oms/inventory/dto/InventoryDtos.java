package com.oms.inventory.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    public record InboundRequest(
            Long warehouseId, Long skuId, int quantity, String batchNo, LocalDate expireAt, String remark) {
    }

    public record ReserveRequest(String orderNo, List<ReserveItem> items) {
    }

    public record ReserveItem(Long skuId, int quantity) {
    }

    public record InventoryResponse(
            Long id,
            Long warehouseId,
            Long skuId,
            String skuNo,
            String batchNo,
            Integer quantity,
            Integer reservedQuantity,
            Integer frozenQuantity,
            LocalDate expireAt) {
    }

    public record TransactionResponse(
            Long id,
            Long warehouseId,
            Long skuId,
            String batchNo,
            Integer bizType,
            String bizNo,
            Integer changeQuantity,
            Integer beforeQuantity,
            Integer afterQuantity,
            Long operatorId,
            String remark,
            LocalDateTime createdAt) {
    }

    public record InventoryAlertResponse(
            Long skuId,
            String skuNo,
            String skuName,
            int availableStock,
            String alertType,
            String message) {
    }

    public record StockTakeItemRequest(Long skuId, Integer expectedQuantity, Integer actualQuantity) {
    }

    public record StockTakeRequest(Long warehouseId, String remark, List<StockTakeItemRequest> items) {
    }

    public record StockTakeItemResponse(
            Long id,
            Long takeId,
            Long skuId,
            Integer expectedQuantity,
            Integer actualQuantity) {
    }

    public record StockTakeResponse(
            Long id,
            String takeNo,
            Long warehouseId,
            Integer status,
            String remark,
            List<StockTakeItemResponse> items) {
    }

    public record WaveCreateRequest(String orderNo, Long skuId, Integer quantity) {
    }

    public record WaveStatusRequest(Integer status) {
    }

    public record WaveResponse(
            Long id,
            String waveNo,
            String orderNo,
            Long skuId,
            Integer quantity,
            Integer status) {
    }

    public record TraceItem(Long inventoryId, Long warehouseId, String batchNo, Integer quantity, LocalDate expireAt) {
    }

    public record TraceResponse(
            Long skuId,
            String skuNo,
            String skuName,
            String udi,
            String registrationNo,
            List<TraceItem> batches) {
    }
}
