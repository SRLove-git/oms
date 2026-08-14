package com.oms.order.dto;

import com.oms.order.dto.OrderDtos.OrderItemRequest;
import com.oms.order.dto.OrderDtos.OrderItemResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商城开放 API 订单 DTO（面向外部商城调用方，与内部 DTO 隔离演进）。
 */
public final class OpenOrderDtos {

    private OpenOrderDtos() {
    }

    public record OpenCreateOrderRequest(
            String externalOrderNo,
            Integer orderType,
            String remark,
            List<OrderItemRequest> items) {
    }

    public record OpenOrderResponse(
            String orderNo,
            String externalOrderNo,
            String source,
            Integer orderType,
            Integer status,
            BigDecimal totalAmount,
            String currency,
            String remark,
            LocalDateTime paidAt,
            LocalDateTime createdAt,
            List<OrderItemResponse> items) {
    }
}
