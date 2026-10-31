package com.oms.payment.controller;

import com.oms.common.core.result.PageResult;
import com.oms.common.core.result.Result;
import com.oms.payment.dto.PaymentDtos.ExternalRefundRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentResponse;
import com.oms.payment.dto.PaymentDtos.PaymentResponse;
import com.oms.payment.dto.PaymentDtos.RefundOutcome;
import com.oms.payment.dto.PaymentDtos.RefundRequest;
import com.oms.payment.service.ExternalPaymentRefundService;
import com.oms.payment.service.PaymentService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final ExternalPaymentRefundService externalPaymentRefundService;

    @Value("${oms.internal-service-secret:change-this-internal-secret}")
    private String internalServiceSecret;

    public PaymentController(
            PaymentService paymentService,
            ExternalPaymentRefundService externalPaymentRefundService) {
        this.paymentService = paymentService;
        this.externalPaymentRefundService = externalPaymentRefundService;
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

    @PostMapping("/external/refund")
    public Result<RefundOutcome> refundExternal(
            @RequestHeader("X-Internal-Secret") String secret,
            @RequestBody ExternalRefundRequest request) {
        requireInternalSecret(secret);
        return Result.ok(externalPaymentRefundService.refund(request));
    }

    @GetMapping("/external/yeahpay-status")
    public Result<Map<String, Object>> yeahPayStatus(
            @RequestHeader("X-Internal-Secret") String secret) {
        requireInternalSecret(secret);
        return Result.ok(externalPaymentRefundService.verifyConfiguration());
    }

    private void requireInternalSecret(String secret) {
        if (!MessageDigest.isEqual(
                internalServiceSecret.getBytes(StandardCharsets.UTF_8),
                secret.getBytes(StandardCharsets.UTF_8))) {
            throw new com.oms.common.core.exception.BusinessException(
                    com.oms.common.core.result.ErrorCode.FORBIDDEN);
        }
    }
}
