package com.oms.payment.adapter;

import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.payment.dto.PaymentDtos.CallbackRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentResponse;
import com.oms.payment.entity.PaymentTransaction;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * 模拟渠道（本地开发/演示）：创建支付即生成回调地址，回调仅校验金额。
 */
@Component
public class MockPaymentAdapter implements PaymentAdapter {

    @Override
    public String channel() {
        return "mock";
    }

    @Override
    public CreatePaymentResponse createPayment(CreatePaymentRequest request, PaymentTransaction transaction) {
        String payUrl = "http://localhost:8085/api/v1/payment-callbacks/mock?paymentNo="
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
        if (request.amount() == null
                || transaction.getAmount().compareTo(request.amount()) != 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "回调金额与支付单不一致");
        }
    }
}
