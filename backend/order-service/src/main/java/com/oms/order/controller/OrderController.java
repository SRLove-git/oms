package com.oms.order.controller;

import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.PageResult;
import com.oms.common.core.result.Result;
import com.oms.order.client.PaymentClient;
import com.oms.order.dto.OrderDtos.CancelRequest;
import com.oms.order.dto.OrderDtos.CreateOrderRequest;
import com.oms.order.dto.OrderDtos.OrderResponse;
import com.oms.order.dto.OrderDtos.OrderSummaryResponse;
import com.oms.order.dto.OrderDtos.PayRequest;
import com.oms.order.dto.OrderDtos.ShipRequest;
import com.oms.order.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public Result<OrderResponse> create(
            @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Username", required = false) String operatorName,
            @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId) {
        if (request.merchantId() == null && merchantId != null) {
            request = new CreateOrderRequest(
                    merchantId, request.orderType(), request.remark(), request.items());
        }
        return Result.ok(orderService.create(request, operatorId, operatorName));
    }

    @GetMapping
    public Result<PageResult<OrderSummaryResponse>> page(
            @RequestHeader(value = "X-User-Type", required = false) Integer userType,
            @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long filterMerchant = (userType != null && userType == 1) ? null : merchantId;
        return Result.ok(orderService.page(filterMerchant, status, page, size));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderResponse> get(
            @PathVariable String orderNo,
            @RequestHeader(value = "X-User-Type", required = false) Integer userType,
            @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId) {
        OrderResponse order = orderService.get(orderNo);
        if (userType == null || userType != 1) {
            if (!order.merchantId().equals(merchantId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
        return Result.ok(order);
    }

    @PostMapping("/{orderNo}/cancel")
    public Result<Void> cancel(
            @PathVariable String orderNo,
            @RequestBody(required = false) CancelRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Username", required = false) String operatorName,
            @RequestHeader(value = "X-User-Type", required = false) Integer userType) {
        orderService.cancel(orderNo, request == null ? new CancelRequest("") : request, operatorId, operatorName, userType);
        return Result.ok();
    }

    @PostMapping("/{orderNo}/pay")
    public Result<PaymentClient.CreatePaymentResponse> pay(
            @PathVariable String orderNo,
            @RequestBody(required = false) PayRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId) {
        return Result.ok(orderService.pay(orderNo, request == null ? new PayRequest("mock") : request, operatorId));
    }

    @PostMapping("/{orderNo}/audit")
    public Result<Void> audit(
            @PathVariable String orderNo,
            @RequestHeader(value = "X-User-Type", required = false) Integer userType,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Username", required = false) String operatorName) {
        requireAdmin(userType);
        orderService.audit(orderNo, operatorId, operatorName);
        return Result.ok();
    }

    @PostMapping("/{orderNo}/ship")
    public Result<Void> ship(
            @PathVariable String orderNo,
            @RequestBody(required = false) ShipRequest request,
            @RequestHeader(value = "X-User-Type", required = false) Integer userType,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Username", required = false) String operatorName) {
        requireAdmin(userType);
        orderService.ship(orderNo, request == null ? new ShipRequest("", "") : request, operatorId, operatorName);
        return Result.ok();
    }

    @PostMapping("/{orderNo}/sign")
    public Result<Void> sign(
            @PathVariable String orderNo,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Username", required = false) String operatorName) {
        orderService.sign(orderNo, operatorId, operatorName);
        return Result.ok();
    }

    @PostMapping("/{orderNo}/complete")
    public Result<Void> complete(
            @PathVariable String orderNo,
            @RequestHeader(value = "X-User-Type", required = false) Integer userType,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Username", required = false) String operatorName) {
        requireAdmin(userType);
        orderService.complete(orderNo, operatorId, operatorName);
        return Result.ok();
    }

    private void requireAdmin(Integer userType) {
        if (userType == null || userType != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
