package com.oms.inventory.service;

import com.oms.common.redis.cache.ReportCache;
import com.oms.inventory.dto.InventoryReportDtos.ExpiryBucket;
import com.oms.inventory.dto.InventoryReportDtos.SlowMovingItem;
import com.oms.inventory.dto.InventoryReportDtos.StockSummary;
import com.oms.inventory.dto.InventoryReportDtos.TurnoverItem;
import com.oms.inventory.dto.InventoryReportDtos.WarehouseStock;
import com.oms.inventory.mapper.InventoryReportMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InventoryReportService {

    private static final Logger log = LoggerFactory.getLogger(InventoryReportService.class);

    private final InventoryReportMapper reportMapper;
    private final ReportCache reportCache;

    @Value("${oms.report.cache-ttl-seconds:300}")
    private long cacheTtlSeconds;

    public InventoryReportService(InventoryReportMapper reportMapper, ReportCache reportCache) {
        this.reportMapper = reportMapper;
        this.reportCache = reportCache;
    }

    public List<WarehouseStock> warehouseStock() {
        return cached("warehouse-stock", List.class, () -> reportMapper.warehouseStock().stream()
                .map(row -> new WarehouseStock(
                        toLong(row.get("warehouse_id")),
                        String.valueOf(row.get("warehouse_name")),
                        toLong(row.get("sku_count")),
                        toLong(row.get("total_quantity")),
                        toLong(row.get("reserved_quantity")),
                        toLong(row.get("frozen_quantity"))))
                .toList());
    }

    public StockSummary stockSummary() {
        List<WarehouseStock> rows = warehouseStock();
        long total = rows.stream().mapToLong(WarehouseStock::totalQuantity).sum();
        long reserved = rows.stream().mapToLong(WarehouseStock::reservedQuantity).sum();
        long frozen = rows.stream().mapToLong(WarehouseStock::frozenQuantity).sum();
        long skuCount = rows.stream().mapToLong(WarehouseStock::skuCount).sum();
        return new StockSummary(total, reserved, frozen, skuCount, rows.size());
    }

    public List<ExpiryBucket> expiryDistribution() {
        return cached("expiry-distribution", List.class, () -> reportMapper.expiryDistribution().stream()
                .map(row -> new ExpiryBucket(
                        String.valueOf(row.get("bucket")),
                        toLong(row.get("sku_count")),
                        toLong(row.get("quantity"))))
                .toList());
    }

    public List<TurnoverItem> turnover(LocalDate startDate, LocalDate endDate, int topN) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        String key = "turnover:" + start + ":" + end + ":" + topN;
        return cached(key, List.class, () -> reportMapper.turnover(start, end, topN).stream()
                .map(row -> {
                    long outbound = toLong(row.get("outbound_quantity"));
                    long currentStock = toLong(row.get("current_stock"));
                    BigDecimal rate = currentStock == 0
                            ? BigDecimal.valueOf(outbound)
                            : BigDecimal.valueOf(outbound)
                                    .divide(BigDecimal.valueOf(currentStock), 2, RoundingMode.HALF_UP);
                    return new TurnoverItem(
                            toLong(row.get("sku_id")),
                            String.valueOf(row.get("sku_no")),
                            String.valueOf(row.get("sku_name")),
                            outbound,
                            currentStock,
                            rate);
                })
                .toList());
    }

    public List<SlowMovingItem> slowMoving(int days, int limit) {
        String key = "slow-moving:" + days + ":" + limit;
        return cached(key, List.class, () -> reportMapper.slowMoving(days, limit).stream()
                .map(row -> new SlowMovingItem(
                        toLong(row.get("sku_id")),
                        String.valueOf(row.get("sku_no")),
                        String.valueOf(row.get("sku_name")),
                        toLong(row.get("current_stock")),
                        row.get("last_sale_at") == null ? null : String.valueOf(row.get("last_sale_at"))))
                .toList());
    }

    private <T> T cached(String key, Class<T> type, Supplier<T> loader) {
        try {
            T hit = reportCache.get(reportCache.key("inventory", key));
            if (hit != null) {
                return hit;
            }
        } catch (Exception ex) {
            log.warn("库存报表缓存读取失败，回退数据库查询 key={}", key);
        }
        T value = loader.get();
        try {
            reportCache.set(reportCache.key("inventory", key), value, Duration.ofSeconds(cacheTtlSeconds));
        } catch (Exception ex) {
            log.warn("库存报表缓存写入失败 key={}", key);
        }
        return value;
    }

    private long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
