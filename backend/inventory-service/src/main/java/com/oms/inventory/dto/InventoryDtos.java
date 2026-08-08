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
}
