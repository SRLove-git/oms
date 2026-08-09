package com.oms.payment.dto;

import java.math.BigDecimal;

public final class PaymentReportDtos {

    private PaymentReportDtos() {
    }

    public record ChannelStats(
            String channel,
            long totalCount,
            long successCount,
            BigDecimal successAmount,
            long failCount,
            long refundCount,
            BigDecimal refundAmount,
            BigDecimal refundRate) {
    }

    public record ReconciliationStats(
            String channel,
            int status,
            long recordCount,
            BigDecimal channelAmount,
            BigDecimal localAmount,
            long diffCount) {
    }
}
