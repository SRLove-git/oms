package com.oms.aftersales.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class OpenAfterSalesDtos {

    private OpenAfterSalesDtos() {
    }

    public record OpenReturnOrderRequest(String externalOrderNo, Integer type, String reason) {
    }

    public record OpenReturnOrderResponse(
            String returnNo,
            String orderNo,
            String externalOrderNo,
            Integer type,
            Integer status,
            String reason,
            BigDecimal totalAmount,
            LocalDateTime createdAt) {
    }
}
