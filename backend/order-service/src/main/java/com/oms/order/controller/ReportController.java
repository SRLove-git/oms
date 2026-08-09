package com.oms.order.controller;

import com.oms.common.core.result.Result;
import com.oms.common.web.util.CsvExportUtil;
import com.oms.order.dto.OrderReportDtos.DailySalesSnapshot;
import com.oms.order.dto.OrderReportDtos.OrderSourceItem;
import com.oms.order.dto.OrderReportDtos.SalesSummary;
import com.oms.order.dto.OrderReportDtos.SalesTrendItem;
import com.oms.order.service.OrderReportService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/sales")
public class ReportController {

    private final OrderReportService reportService;

    public ReportController(OrderReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    public Result<SalesSummary> summary(
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate,
            @RequestParam(required = false) Long merchantId) {
        return Result.ok(reportService.summary(range(startDate, 30), range(endDate, 0), merchantId));
    }

    @GetMapping("/trend")
    public Result<List<SalesTrendItem>> trend(
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate,
            @RequestParam(required = false) Long merchantId) {
        return Result.ok(reportService.trend(range(startDate, 30), range(endDate, 0), merchantId));
    }

    @GetMapping("/source")
    public Result<List<OrderSourceItem>> source(
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate,
            @RequestParam(required = false) Long merchantId) {
        return Result.ok(reportService.source(range(startDate, 30), range(endDate, 0), merchantId));
    }

    @GetMapping("/daily")
    public Result<List<DailySalesSnapshot>> daily(
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate) {
        return Result.ok(reportService.daily(range(startDate, 30), range(endDate, 0)));
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
            @RequestParam(required = false) Long merchantId,
            HttpServletResponse response)
            throws IOException {
        LocalDate start = range(startDate, 30);
        LocalDate end = range(endDate, 0);
        switch (type) {
            case "summary" -> {
                SalesSummary s = reportService.summary(start, end, merchantId);
                CsvExportUtil.write(
                        response,
                        "sales-summary",
                        List.of("订单数", "支付订单数", "支付金额", "客单价", "毛利", "退款金额", "复购率(%)"),
                        List.of(List.of(
                                s.orderCount(),
                                s.paidOrderCount(),
                                s.paidAmount(),
                                s.avgOrderValue(),
                                s.grossProfit(),
                                s.refundAmount(),
                                s.repurchaseRate())));
            }
            case "trend" -> {
                List<SalesTrendItem> rows = reportService.trend(start, end, merchantId);
                CsvExportUtil.write(
                        response,
                        "sales-trend",
                        List.of("日期", "支付订单数", "支付金额"),
                        rows.stream()
                                .map(r -> List.<Object>of(r.bizDate(), r.paidOrderCount(), r.paidAmount()))
                                .toList());
            }
            case "source" -> {
                List<OrderSourceItem> rows = reportService.source(start, end, merchantId);
                CsvExportUtil.write(
                        response,
                        "sales-source",
                        List.of("订单类型", "订单数", "支付金额"),
                        rows.stream()
                                .map(r -> List.<Object>of(orderTypeName(r.orderType()), r.orderCount(), r.paidAmount()))
                                .toList());
            }
            case "daily" -> {
                List<DailySalesSnapshot> rows = reportService.daily(start, end);
                CsvExportUtil.write(
                        response,
                        "sales-daily",
                        List.of("业务日期", "订单数", "支付订单数", "支付金额", "毛利", "退款金额"),
                        rows.stream()
                                .map(r -> List.<Object>of(
                                        r.bizDate(),
                                        r.orderCount(),
                                        r.paidOrderCount(),
                                        r.paidAmount(),
                                        r.grossProfit(),
                                        r.refundAmount()))
                                .toList());
            }
            default -> throw new IllegalArgumentException("不支持的导出类型: " + type);
        }
    }

    private LocalDate range(LocalDate date, int defaultOffsetDays) {
        return date == null ? LocalDate.now().minusDays(defaultOffsetDays) : date;
    }

    private String orderTypeName(int type) {
        return type == 2 ? "B2C 终端客户" : "B2B 商户";
    }
}
