package com.oms.payment.psp;

import com.oms.payment.dto.PaymentDtos.CreatePaymentResponse;
import com.oms.payment.dto.PaymentDtos.RefundRequest;
import com.oms.payment.entity.PaymentTransaction;

/**
 * 国际卡 PSP 客户端抽象：真实收单服务（Stripe / Adyen / Payment Asia / BBMSL 等）实现该契约，
 * 支付中心只依赖托管结算页 + 签名回调 + 退款三个能力，切换 PSP 时替换实现即可。
 */
public interface CardPspClient {

    boolean isConfigured();

    CreatePaymentResponse createCheckoutSession(PaymentTransaction transaction);

    CardWebhookEvent parseWebhook(String payload, String signatureHeader);

    String refund(PaymentTransaction transaction, RefundRequest request);
}
