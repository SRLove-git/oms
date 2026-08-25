package com.oms.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class BalanceDtos {

    private BalanceDtos() {
    }

    public record BalanceResponse(
            Long merchantId, BigDecimal availableAmount, BigDecimal frozenAmount) {
    }

    public record RechargeRequest(Long merchantId, BigDecimal amount, String remark) {
    }

    public record BalanceTransactionResponse(
            Long id,
            Long merchantId,
            String bizNo,
            Integer type,
            BigDecimal amount,
            BigDecimal beforeAmount,
            BigDecimal afterAmount,
            String remark,
            LocalDateTime createdAt) {
    }
}
