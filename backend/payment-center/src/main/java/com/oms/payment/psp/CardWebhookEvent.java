package com.oms.payment.psp;

import java.math.BigDecimal;

/**
 * 国际卡 PSP 签名回调归一化结果。
 */
public record CardWebhookEvent(
        String paymentNo, String channelTxnNo, BigDecimal amount, String currency) {
}
