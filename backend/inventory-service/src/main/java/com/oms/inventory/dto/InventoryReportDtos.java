package com.oms.inventory.dto;

import java.math.BigDecimal;

public final class InventoryReportDtos {

    private InventoryReportDtos() {
    }

    public record WarehouseStock(
            Long warehouseId,
            String warehouseName,
            long skuCount,
            long totalQuantity,
            long reservedQuantity,
            long frozenQuantity) {
    }

    public record ExpiryBucket(String bucket, long skuCount, long quantity) {
    }

    public record TurnoverItem(
            Long skuId,
            String skuNo,
            String skuName,
            long outboundQuantity,
            long currentStock,
            BigDecimal turnoverRate) {
    }

    public record SlowMovingItem(
            Long skuId, String skuNo, String skuName, long currentStock, String lastSaleAt) {
    }

    public record StockSummary(
            long totalQuantity,
            long reservedQuantity,
            long frozenQuantity,
            long skuCount,
            long warehouseCount) {
    }
}
