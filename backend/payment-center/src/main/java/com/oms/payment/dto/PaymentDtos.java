package com.oms.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record CreatePaymentRequest(
            String orderNo, BigDecimal amount, String currency, String channel, Long merchantId) {
    }

    public record CreatePaymentResponse(String paymentNo, String channel, String payUrl, BigDecimal amount) {
    }

    public record CallbackRequest(String paymentNo, String channelTxnNo, BigDecimal amount, String status) {
    }

    public record PaymentResponse(
            Long id,
            String paymentNo,
            String orderNo,
            String channel,
            BigDecimal amount,
            String currency,
            Integer status,
            String channelTxnNo,
            LocalDateTime createdAt,
            LocalDateTime paidAt) {
    }

    public record RefundRequest(BigDecimal amount, Integer method) {
    }
}
