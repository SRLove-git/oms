package com.oms.payment.adapter;

import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.payment.dto.PaymentDtos.CallbackRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentResponse;
import com.oms.payment.dto.PaymentDtos.RefundRequest;
import com.oms.payment.entity.PaymentTransaction;

/**
 * 支付渠道适配器：统一创建支付、回调验签与退款。
 */
public interface PaymentAdapter {

    String channel();

    CreatePaymentResponse createPayment(CreatePaymentRequest request, PaymentTransaction transaction);

    /**
     * 校验回调；验签失败抛出 BusinessException。
     */
    void verifyCallback(CallbackRequest request, PaymentTransaction transaction);

    /**
     * 渠道退款；返回渠道退款单号，纯本地渠道可返回 null。
     * 默认抛错，避免未接入渠道被误当作退款成功。
     */
    default String refund(PaymentTransaction transaction, RefundRequest request) {
        throw new BusinessException(
                ErrorCode.BAD_REQUEST.getCode(), "该支付渠道不支持在线退款");
    }
}
