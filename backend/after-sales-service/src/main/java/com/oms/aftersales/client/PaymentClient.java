package com.oms.aftersales.client;

import com.oms.common.core.result.Result;
import java.math.BigDecimal;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-center", path = "/api/v1/payments")
public interface PaymentClient {

    @PostMapping("/{paymentNo}/refund")
    Result<Void> refund(@PathVariable String paymentNo, @RequestBody RefundRequest request);

    record RefundRequest(BigDecimal amount, String reason) {
    }
}
