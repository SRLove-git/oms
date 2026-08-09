package com.oms.aftersales.dto;

import java.math.BigDecimal;

public final class AfterSalesReportDtos {

    private AfterSalesReportDtos() {
    }

    public record TypeStats(
            int type,
            long count,
            BigDecimal totalAmount,
            long completedCount,
            BigDecimal refundedAmount) {
    }

    public record ReasonDistribution(String reason, long count) {
    }

    public record RepairDuration(long repairCount, long avgMinutes, long minMinutes, long maxMinutes) {
    }

    public record ReturnRate(long returnCount, long completedOrderCount, BigDecimal rate) {
    }
}
