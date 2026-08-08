package com.oms.payment.adapter;

import com.oms.payment.dto.PaymentDtos.CallbackRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentResponse;
import com.oms.payment.entity.PaymentTransaction;

/**
 * 支付渠道适配器：统一创建支付与回调验签。
 */
public interface PaymentAdapter {

    String channel();

    CreatePaymentResponse createPayment(CreatePaymentRequest request, PaymentTransaction transaction);

    /**
     * 校验回调；验签失败抛出 BusinessException。
     */
    void verifyCallback(CallbackRequest request, PaymentTransaction transaction);
}
