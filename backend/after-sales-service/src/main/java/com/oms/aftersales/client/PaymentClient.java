package com.oms.aftersales.client;

import com.oms.common.core.result.Result;
import java.math.BigDecimal;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "payment-center", path = "/api/v1/payments")
public interface PaymentClient {

    @PostMapping("/{paymentNo}/refund")
    Result<Void> refund(@PathVariable String paymentNo, @RequestBody RefundRequest request);

    @PostMapping("/external/refund")
    Result<RefundOutcome> refundExternal(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @RequestBody ExternalRefundRequest request);

    record RefundRequest(BigDecimal amount, String reason) {
    }

    record ExternalRefundRequest(
            String paymentNo,
            String orderNo,
            String channel,
            BigDecimal amount,
            String currency,
            String channelTxnNo,
            String providerOrderNo,
            String merchantRefundId) {
    }

    /** status: 2=成功，3=退款中，1=失败（对应 YeahPay 统一退款响应）。 */
    record RefundOutcome(Integer status, String providerRefundNo, String message) {
    }
}
