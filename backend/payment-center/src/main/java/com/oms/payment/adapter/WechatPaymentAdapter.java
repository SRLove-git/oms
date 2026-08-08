package com.oms.payment.adapter;

import com.oms.common.core.exception.BusinessException;
import com.oms.payment.dto.PaymentDtos.CallbackRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentResponse;
import com.oms.payment.entity.PaymentTransaction;
import org.springframework.stereotype.Component;

/**
 * 微信支付适配器：商户号/证书配置就绪后接入官方 SDK。
 */
@Component
public class WechatPaymentAdapter implements PaymentAdapter {

    @Override
    public String channel() {
        return "wechat";
    }

    @Override
    public CreatePaymentResponse createPayment(CreatePaymentRequest request, PaymentTransaction transaction) {
        throw new BusinessException("微信支付渠道接入待配置");
    }

    @Override
    public void verifyCallback(CallbackRequest request, PaymentTransaction transaction) {
        throw new BusinessException("微信支付渠道接入待配置");
    }
}
