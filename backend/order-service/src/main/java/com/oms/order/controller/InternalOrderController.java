package com.oms.order.controller;

import com.oms.common.core.result.Result;
import com.oms.order.dto.OrderDtos.PaymentSuccessRequest;
import com.oms.order.dto.OrderDtos.OrderResponse;
import com.oms.order.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务间内部接口（支付中心回调通知，不走网关认证）。
 */
@RestController
@RequestMapping("/api/v1/orders/internal")
public class InternalOrderController {

    private final OrderService orderService;

    public InternalOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{orderNo}")
    public Result<OrderResponse> getInternal(@PathVariable String orderNo) {
        return Result.ok(orderService.get(orderNo));
    }

    @PostMapping("/payment-success")
    public Result<Void> paymentSuccess(@RequestBody PaymentSuccessRequest request) {
        orderService.handlePaymentSuccess(request);
        return Result.ok();
    }

    @PostMapping("/{orderNo}/after-sales")
    public Result<Void> afterSales(
            @PathVariable String orderNo,
            @RequestBody AfterSalesNotifyRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Username", required = false) String operatorName) {
        orderService.markAfterSales(orderNo, request.returnNo(), request.type(), operatorId, operatorName);
        return Result.ok();
    }

    @PostMapping("/{orderNo}/after-sales-complete")
    public Result<Void> afterSalesComplete(
            @PathVariable String orderNo,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Username", required = false) String operatorName) {
        orderService.completeAfterSales(orderNo, operatorId, operatorName);
        return Result.ok();
    }

    public record AfterSalesNotifyRequest(String returnNo, Integer type, Integer orderStatus) {
    }
}
