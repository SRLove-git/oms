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
            String consignee,
            String phone,
            String address,
            BigDecimal deliveryFee,
            List<OrderItemRequest> items) {
    }

    /**
     * 商城支付成功通知：商城侧收款完成后回调 OMS，订单由待支付推进为已支付并扣减库存。
     */
    public record OpenPaymentNotifyRequest(
            String paymentNo,
            BigDecimal amount,
            String channel,
            String channelTxnNo,
            LocalDateTime paidAt) {
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
            String consignee,
            String phone,
            String address,
            BigDecimal deliveryFee,
            LocalDateTime paidAt,
            LocalDateTime createdAt,
            List<OrderItemResponse> items) {
    }
}
