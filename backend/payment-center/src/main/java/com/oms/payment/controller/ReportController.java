package com.oms.payment.controller;

import com.oms.common.core.result.Result;
import com.oms.common.web.util.CsvExportUtil;
import com.oms.payment.dto.PaymentReportDtos.ChannelStats;
import com.oms.payment.dto.PaymentReportDtos.ReconciliationStats;
import com.oms.payment.service.PaymentReportService;
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
@RequestMapping("/api/v1/reports/payments")
public class ReportController {

    private final PaymentReportService reportService;

    public ReportController(PaymentReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/channel-stats")
    public Result<List<ChannelStats>> channelStats(
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        return Result.ok(reportService.channelStats(start, end));
    }

    @GetMapping("/reconciliation-stats")
    public Result<List<ReconciliationStats>> reconciliationStats(
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        return Result.ok(reportService.reconciliationStats(start, end));
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
            HttpServletResponse response)
            throws IOException {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        switch (type) {
            case "channel" -> {
                List<ChannelStats> rows = reportService.channelStats(start, end);
                CsvExportUtil.write(
                        response,
                        "payment-channel",
                        List.of("渠道", "总笔数", "成功笔数", "成功金额", "失败笔数", "退款笔数", "退款金额", "退款率(%)"),
                        rows.stream()
                                .map(r -> List.<Object>of(
                                        r.channel(),
                                        r.totalCount(),
                                        r.successCount(),
                                        r.successAmount(),
                                        r.failCount(),
                                        r.refundCount(),
                                        r.refundAmount(),
                                        r.refundRate()))
                                .toList());
            }
            case "reconciliation" -> {
                List<ReconciliationStats> rows = reportService.reconciliationStats(start, end);
                CsvExportUtil.write(
                        response,
                        "payment-reconciliation",
                        List.of("渠道", "状态", "记录数", "渠道金额", "本地金额", "差异笔数"),
                        rows.stream()
                                .map(r -> List.<Object>of(
                                        r.channel(),
                                        reconciliationStatusName(r.status()),
                                        r.recordCount(),
                                        r.channelAmount(),
                                        r.localAmount(),
                                        r.diffCount()))
                                .toList());
            }
            default -> throw new IllegalArgumentException("不支持的导出类型: " + type);
        }
    }

    private String reconciliationStatusName(int status) {
        return switch (status) {
            case 1 -> "差异待处理";
            case 2 -> "已处理";
            case 3 -> "一致";
            default -> "未知";
        };
    }
}
