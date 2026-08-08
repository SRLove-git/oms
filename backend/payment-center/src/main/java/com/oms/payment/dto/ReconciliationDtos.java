package com.oms.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ReconciliationDtos {

    private ReconciliationDtos() {
    }

    public record RunRequest(LocalDate bizDate, String channel, boolean simulateDiff) {
    }

    public record DiffItem(
            String paymentNo,
            String orderNo,
            BigDecimal channelAmount,
            BigDecimal localAmount,
            String type) {
    }

    public record ReconciliationResponse(
            Long id,
            LocalDate bizDate,
            String channel,
            BigDecimal channelAmount,
            BigDecimal localAmount,
            Integer diffCount,
            Integer status,
            List<DiffItem> diffs,
            LocalDateTime handledAt,
            LocalDateTime createdAt) {
    }
}
