package com.oms.aftersales.controller;

import com.oms.aftersales.dto.AfterSalesReportDtos.ReasonDistribution;
import com.oms.aftersales.dto.AfterSalesReportDtos.RepairDuration;
import com.oms.aftersales.dto.AfterSalesReportDtos.ReturnRate;
import com.oms.aftersales.dto.AfterSalesReportDtos.TypeStats;
import com.oms.aftersales.service.AfterSalesReportService;
import com.oms.common.core.result.Result;
import com.oms.common.web.util.CsvExportUtil;
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
@RequestMapping("/api/v1/reports/aftersales")
public class ReportController {

    private final AfterSalesReportService reportService;

    public ReportController(AfterSalesReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/type-stats")
    public Result<List<TypeStats>> typeStats(
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        return Result.ok(reportService.typeStats(start, end));
    }

    @GetMapping("/reason-distribution")
    public Result<List<ReasonDistribution>> reasonDistribution(
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate,
            @RequestParam(defaultValue = "10") int topN) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        return Result.ok(reportService.reasonDistribution(start, end, topN));
    }

    @GetMapping("/repair-duration")
    public Result<RepairDuration> repairDuration(
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        return Result.ok(reportService.repairDuration(start, end));
    }

    @GetMapping("/return-rate")
    public Result<ReturnRate> returnRate(
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        return Result.ok(reportService.returnRate(start, end));
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
            HttpServletResponse response)
            throws IOException {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        switch (type) {
            case "type" -> {
                List<TypeStats> rows = reportService.typeStats(start, end);
                CsvExportUtil.write(
                        response,
                        "aftersales-type",
                        List.of("售后类型", "单量", "金额", "已完成", "退款金额"),
                        rows.stream()
                                .map(r -> List.<Object>of(
                                        typeName(r.type()),
                                        r.count(),
                                        r.totalAmount(),
                                        r.completedCount(),
                                        r.refundedAmount()))
                                .toList());
            }
            case "reason" -> {
                List<ReasonDistribution> rows = reportService.reasonDistribution(start, end, topN);
                CsvExportUtil.write(
                        response,
                        "aftersales-reason",
                        List.of("原因", "单量"),
                        rows.stream()
                                .map(r -> List.<Object>of(r.reason(), r.count()))
                                .toList());
            }
            case "repair" -> {
                RepairDuration r = reportService.repairDuration(start, end);
                CsvExportUtil.write(
                        response,
                        "aftersales-repair",
                        List.of("维修工单数", "平均时长(分钟)", "最短(分钟)", "最长(分钟)"),
                        List.of(List.of(
                                r.repairCount(), r.avgMinutes(), r.minMinutes(), r.maxMinutes())));
            }
            case "return-rate" -> {
                ReturnRate r = reportService.returnRate(start, end);
                CsvExportUtil.write(
                        response,
                        "aftersales-return-rate",
                        List.of("退货单量", "已完成订单数", "退货率(%)"),
                        List.of(List.of(r.returnCount(), r.completedOrderCount(), r.rate())));
            }
            default -> throw new IllegalArgumentException("不支持的导出类型: " + type);
        }
    }

    private String typeName(int type) {
        return switch (type) {
            case 1 -> "退货";
            case 2 -> "换货";
            case 3 -> "维修";
            default -> "未知";
        };
    }
}
