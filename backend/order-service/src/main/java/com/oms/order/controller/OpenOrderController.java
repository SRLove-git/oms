package com.oms.order.controller;

import com.oms.common.core.result.Result;
import com.oms.order.dto.OpenOrderDtos.OpenCreateOrderRequest;
import com.oms.order.dto.OpenOrderDtos.OpenOrderResponse;
import com.oms.order.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商城开放 API：外部商城/平台经网关签名校验后调用（见网关 OpenApiAuthFilter）。
 *
 * <ul>
 *   <li>下单幂等：以 {@code externalOrderNo} 为幂等键，重复提交返回同一订单；</li>
 *   <li>租户隔离：{@code X-Merchant-Id} 由网关按 appId 映射注入，商户维度强制校验；</li>
 *   <li>审计：操作人记为 {@code OPEN_API}，订单状态流转全程留痕。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/open/orders")
public class OpenOrderController {

    private final OrderService orderService;

    public OpenOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public Result<OpenOrderResponse> create(
            @RequestBody OpenCreateOrderRequest request,
            @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId) {
        return Result.ok(orderService.createOpen(request, merchantId));
    }

    @GetMapping("/{externalOrderNo}")
    public Result<OpenOrderResponse> get(
            @PathVariable String externalOrderNo,
            @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId) {
        return Result.ok(orderService.getOpen(externalOrderNo, merchantId));
    }

    @PostMapping("/{externalOrderNo}/cancel")
    public Result<Void> cancel(
            @PathVariable String externalOrderNo,
            @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId) {
        orderService.cancelOpen(externalOrderNo, merchantId);
        return Result.ok();
    }
}
