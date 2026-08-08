package com.oms.payment.controller;

import com.oms.common.core.result.PageResult;
import com.oms.common.core.result.Result;
import com.oms.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentResponse;
import com.oms.payment.dto.PaymentDtos.PaymentResponse;
import com.oms.payment.dto.PaymentDtos.RefundRequest;
import com.oms.payment.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public Result<CreatePaymentResponse> create(@RequestBody CreatePaymentRequest request) {
        return Result.ok(paymentService.create(request));
    }

    @GetMapping
    public Result<PageResult<PaymentResponse>> page(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(paymentService.page(orderNo, status, page, size));
    }

    @PostMapping("/{paymentNo}/refund")
    public Result<Void> refund(@PathVariable String paymentNo, @RequestBody RefundRequest request) {
        paymentService.refund(paymentNo, request);
        return Result.ok();
    }
}
