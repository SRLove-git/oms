package com.oms.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CreateOrderRequest(
            Long merchantId, Integer orderType, String remark, List<OrderItemRequest> items) {
    }

    public record OrderItemRequest(Long skuId, int quantity) {
    }

    public record OrderItemResponse(
            Long id,
            Long skuId,
            String skuName,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice) {
    }

    public record OrderLogResponse(
            Integer fromStatus,
            Integer toStatus,
            String operatorName,
            String remark,
            LocalDateTime createdAt) {
    }

    public record OrderResponse(
            Long id,
            String orderNo,
            Long merchantId,
            Integer orderType,
            Integer status,
            BigDecimal totalAmount,
            BigDecimal payAmount,
            String currency,
            String remark,
            String consignee,
            String phone,
            String address,
            LocalDateTime paidAt,
            LocalDateTime timeoutAt,
            LocalDateTime createdAt,
            List<OrderItemResponse> items,
            List<OrderLogResponse> logs) {
    }

    public record OrderSummaryResponse(
            Long id,
            String orderNo,
            Long merchantId,
            Integer orderType,
            Integer status,
            BigDecimal totalAmount,
            BigDecimal payAmount,
            LocalDateTime createdAt,
            int itemCount) {
    }

    public record CancelRequest(String reason) {
    }

    public record PayRequest(String channel, BigDecimal amount) {

        public PayRequest(String channel) {
            this(channel, null);
        }
    }

    public record ShipRequest(String trackingNo, String carrier) {
    }

    public record LogisticsEventRequest(
            String orderNo,
            String carrier,
            String trackingNo,
            String logisticsStatus,
            String trace) {
    }

    public record PaymentSuccessRequest(
            String orderNo, String paymentNo, String channel, BigDecimal amount, String channelTxnNo) {
    }

    public record OrderPaymentState(
            String orderNo,
            Long merchantId,
            BigDecimal payAmount,
            String currency,
            BigDecimal paidAmount,
            Integer status) {
    }

    public record ApprovalRequest(Integer approvalType, String requestPayload) {
    }

    public record ApprovalReviewRequest(Integer status, String requestPayload) {
    }

    public record ApprovalResponse(
            Long id,
            String orderNo,
            Integer approvalType,
            String requestPayload,
            Integer status,
            String operatorName,
            LocalDateTime createdAt) {
    }
}
