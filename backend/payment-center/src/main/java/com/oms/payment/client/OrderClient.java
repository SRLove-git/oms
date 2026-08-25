package com.oms.payment.client;

import com.oms.common.core.result.Result;
import java.math.BigDecimal;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "order-service", path = "/api/v1/orders/internal")
public interface OrderClient {

    @PostMapping("/payment-success")
    Result<Void> notifyPaymentSuccess(@RequestBody PaymentSuccessRequest request);

    @GetMapping("/{orderNo}/payment-state")
    Result<OrderPaymentState> getPaymentState(@PathVariable("orderNo") String orderNo);

    record PaymentSuccessRequest(
            String orderNo, String paymentNo, String channel, BigDecimal amount, String channelTxnNo) {
    }

    record OrderPaymentState(
            String orderNo,
            Long merchantId,
            BigDecimal payAmount,
            String currency,
            BigDecimal paidAmount,
            Integer status) {
    }
}
