package com.oms.payment.adapter;

import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.payment.dto.PaymentDtos.CallbackRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentResponse;
import com.oms.payment.dto.PaymentDtos.RefundRequest;
import com.oms.payment.entity.PaymentTransaction;
import com.oms.payment.psp.CardPspClient;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * 国际卡渠道适配器基类：真实 PSP（Stripe / Adyen / Payment Asia / BBMSL 等）已配置时走托管结算页，
 * 未配置或模拟模式下回退为本地 mock，保证开发/演示链路不阻塞。
 */
public abstract class AbstractCardPaymentAdapter implements PaymentAdapter {

    private static final String MOCK_CALLBACK_BASE = "http://localhost:8085/api/v1/payment-callbacks/";

    @Autowired(required = false)
    private CardPspClient cardPspClient;

    @Value("${oms.payment.mock-only:true}")
    private Boolean mockOnly;

    @Override
    public CreatePaymentResponse createPayment(CreatePaymentRequest request, PaymentTransaction transaction) {
        if (!mockMode() && !realConfigured()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE.getCode(), "国际卡支付未配置 PSP 密钥");
        }
        if (!mockMode() && realConfigured()) {
            return cardPspClient.createCheckoutSession(transaction);
        }

        String payUrl = MOCK_CALLBACK_BASE
                + channel()
                + "?paymentNo="
                + transaction.getPaymentNo()
                + "&amount="
                + transaction.getAmount();
        return new CreatePaymentResponse(
                transaction.getPaymentNo(), channel(), payUrl, transaction.getAmount());
    }

    @Override
    public void verifyCallback(CallbackRequest request, PaymentTransaction transaction) {
        if (!mockMode()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), "国际卡支付请使用签名 webhook 端点回调");
        }
        if (!"SUCCESS".equalsIgnoreCase(request.status())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "支付状态非成功");
        }
        BigDecimal amount = request.amount();
        if (amount == null || transaction.getAmount().compareTo(amount) != 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "回调金额与支付单不一致");
        }
    }

    @Override
    public String refund(PaymentTransaction transaction, RefundRequest request) {
        if (!mockMode()) {
            if (!realConfigured()) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE.getCode(), "国际卡支付未配置 PSP 密钥");
            }
            return cardPspClient.refund(transaction, request);
        }
        return null;
    }

    /**
     * 未注入（如单元测试 new 实例）时默认按模拟模式处理，保持既有测试与本地演示可用。
     */
    private boolean mockMode() {
        return !Boolean.FALSE.equals(mockOnly);
    }

    private boolean realConfigured() {
        return cardPspClient != null && cardPspClient.isConfigured();
    }
}
