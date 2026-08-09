package com.oms.inventory.controller;

import com.oms.common.core.result.Result;
import com.oms.common.web.util.CsvExportUtil;
import com.oms.inventory.dto.InventoryReportDtos.ExpiryBucket;
import com.oms.inventory.dto.InventoryReportDtos.SlowMovingItem;
import com.oms.inventory.dto.InventoryReportDtos.StockSummary;
import com.oms.inventory.dto.InventoryReportDtos.TurnoverItem;
import com.oms.inventory.dto.InventoryReportDtos.WarehouseStock;
import com.oms.inventory.service.InventoryReportService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/inventory")
public class ReportController {

    private final InventoryReportService reportService;

    public ReportController(InventoryReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/warehouse-stock")
    public Result<List<WarehouseStock>> warehouseStock() {
        return Result.ok(reportService.warehouseStock());
    }

    @GetMapping("/stock-summary")
    public Result<StockSummary> stockSummary() {
        return Result.ok(reportService.stockSummary());
    }

    @GetMapping("/expiry-distribution")
    public Result<List<ExpiryBucket>> expiryDistribution() {
        return Result.ok(reportService.expiryDistribution());
    }

    @GetMapping("/turnover")
    public Result<List<TurnoverItem>> turnover(
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate,
            @RequestParam(defaultValue = "10") int topN) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        return Result.ok(reportService.turnover(start, end, Math.min(Math.max(topN, 1), 100)));
    }

    @GetMapping("/slow-moving")
    public Result<List<SlowMovingItem>> slowMoving(
            @RequestParam(defaultValue = "90") int days,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(reportService.slowMoving(days, Math.min(Math.max(limit, 1), 500)));
    }

    @GetMapping("/export")
    public void export(
            @RequestParam String type,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate,
            @RequestParam(defaultValue = "10") int topN,
            @RequestParam(defaultValue = "90") int days,
            @RequestParam(defaultValue = "50") int limit,
            HttpServletResponse response)
            throws IOException {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        switch (type) {
            case "warehouse-stock" -> {
                List<WarehouseStock> rows = reportService.warehouseStock();
                CsvExportUtil.write(
                        response,
                        "inventory-warehouse-stock",
                        List.of("仓库", "SKU数", "可用库存", "预占库存", "冻结库存"),
                        rows.stream()
                                .map(r -> List.<Object>of(
                                        r.warehouseName(),
                                        r.skuCount(),
                                        r.totalQuantity(),
                                        r.reservedQuantity(),
                                        r.frozenQuantity()))
                                .toList());
            }
            case "expiry" -> {
                List<ExpiryBucket> rows = reportService.expiryDistribution();
                CsvExportUtil.write(
                        response,
                        "inventory-expiry",
                        List.of("效期区间", "SKU数", "库存量"),
                        rows.stream()
                                .map(r -> List.<Object>of(r.bucket(), r.skuCount(), r.quantity()))
                                .toList());
            }
            case "turnover" -> {
                List<TurnoverItem> rows = reportService.turnover(start, end, topN);
                CsvExportUtil.write(
                        response,
                        "inventory-turnover",
                        List.of("SKU编码", "SKU名称", "出库量", "当前库存", "周转率"),
                        rows.stream()
                                .map(r -> List.<Object>of(
                                        r.skuNo(), r.skuName(), r.outboundQuantity(), r.currentStock(), r.turnoverRate()))
                                .toList());
            }
            case "slow-moving" -> {
                List<SlowMovingItem> rows = reportService.slowMoving(days, limit);
                CsvExportUtil.write(
                        response,
                        "inventory-slow-moving",
                        List.of("SKU编码", "SKU名称", "当前库存", "最后动销时间"),
                        rows.stream()
                                .map(r -> List.<Object>of(r.skuNo(), r.skuName(), r.currentStock(), r.lastSaleAt()))
                                .toList());
            }
            default -> throw new IllegalArgumentException("不支持的导出类型: " + type);
        }
    }
}
