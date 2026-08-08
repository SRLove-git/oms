package com.oms.order.controller;

import com.oms.common.core.result.Result;
import com.oms.order.dto.OrderDtos.PaymentSuccessRequest;
import com.oms.order.service.OrderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/payment-success")
    public Result<Void> paymentSuccess(@RequestBody PaymentSuccessRequest request) {
        orderService.handlePaymentSuccess(request);
        return Result.ok();
    }
}
