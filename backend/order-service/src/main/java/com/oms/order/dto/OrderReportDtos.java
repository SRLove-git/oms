package com.oms.order.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class OrderReportDtos {

    private OrderReportDtos() {
    }

    public record SalesSummary(
            long orderCount,
            long paidOrderCount,
            BigDecimal paidAmount,
            BigDecimal avgOrderValue,
            BigDecimal grossProfit,
            BigDecimal refundAmount,
            long totalCustomers,
            long repeatCustomers,
            BigDecimal repurchaseRate) {
    }

    public record SalesTrendItem(LocalDate bizDate, long paidOrderCount, BigDecimal paidAmount) {
    }

    public record OrderSourceItem(int orderType, long orderCount, BigDecimal paidAmount) {
    }

    public record DailySalesSnapshot(
            LocalDate bizDate,
            long orderCount,
            long paidOrderCount,
            BigDecimal paidAmount,
            BigDecimal grossProfit,
            BigDecimal refundAmount) {
    }

    public record CompletedOrderCount(long completedCount) {
    }
}
