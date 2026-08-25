package com.oms.payment.adapter;

import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.payment.dto.PaymentDtos.CallbackRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentResponse;
import com.oms.payment.entity.PaymentTransaction;
import java.math.BigDecimal;

/**
 * 国际卡 PSP 模拟适配器：真实 PSP（Payment Asia / BBMSL 等）商户号与证书就绪后，
 * 仅需替换 createPayment / verifyCallback 的内部实现，对外契约保持不变。
 */
public abstract class AbstractMockCardPaymentAdapter implements PaymentAdapter {

    @Override
    public CreatePaymentResponse createPayment(CreatePaymentRequest request, PaymentTransaction transaction) {
        String payUrl = "http://localhost:8085/api/v1/payment-callbacks/"
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
        if (!"SUCCESS".equalsIgnoreCase(request.status())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "支付状态非成功");
        }
        BigDecimal amount = request.amount();
        if (amount == null || transaction.getAmount().compareTo(amount) != 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "回调金额与支付单不一致");
        }
    }
}
