package com.oms.payment.controller;

import com.oms.common.core.result.Result;
import com.oms.payment.dto.PaymentDtos.CallbackRequest;
import com.oms.payment.service.PaymentService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付渠道回调入口（外部渠道调用，验签 + 幂等）。
 */
@RestController
@RequestMapping("/api/v1/payment-callbacks")
public class PaymentCallbackController {

    private final PaymentService paymentService;

    public PaymentCallbackController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{channel}")
    public Result<Void> callback(@PathVariable String channel, @RequestBody CallbackRequest request) {
        paymentService.handleCallback(channel, request);
        return Result.ok();
    }
}
