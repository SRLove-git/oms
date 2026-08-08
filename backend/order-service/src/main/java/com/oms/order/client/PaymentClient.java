package com.oms.order.client;

import com.oms.common.core.result.Result;
import java.math.BigDecimal;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-center", path = "/api/v1")
public interface PaymentClient {

    @PostMapping("/payments")
    Result<CreatePaymentResponse> create(@RequestBody CreatePaymentRequest request);

    record CreatePaymentRequest(
            String orderNo, BigDecimal amount, String currency, String channel, Long merchantId) {
    }

    record CreatePaymentResponse(String paymentNo, String channel, String payUrl, BigDecimal amount) {
    }
}
