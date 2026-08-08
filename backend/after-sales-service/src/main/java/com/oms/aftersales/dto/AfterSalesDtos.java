package com.oms.aftersales.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AfterSalesDtos {

    private AfterSalesDtos() {
    }

    public record ApplyRequest(
            String orderNo,
            Integer type,
            String reason,
            List<ApplyItemRequest> items) {
    }

    public record ApplyItemRequest(Long orderItemId, Long skuId, int quantity) {
    }

    public record ReviewRequest(boolean approved, String reason) {
    }

    public record InspectRequest(boolean qualified, String remark) {
    }

    public record RefundRequest(String paymentNo, BigDecimal amount, Integer method) {
    }

    public record RepairCreateRequest(Long skuId, String faultDesc, String assignedTo) {
    }

    public record RepairProgressRequest(String action, String content) {
    }

    public record RepairFeeRequest(BigDecimal repairFee) {
    }

    public record ReturnItemResponse(
            Long id,
            Long orderItemId,
            Long skuId,
            Integer quantity,
            BigDecimal unitAmount) {
    }

    public record RefundRecordResponse(
            String refundNo,
            String paymentNo,
            BigDecimal amount,
            Integer method,
            Integer status,
            String channelTxnNo,
            LocalDateTime refundedAt) {
    }

    public record RepairResponse(
            Long id,
            String repairNo,
            String returnNo,
            Long skuId,
            Integer status,
            String faultDesc,
            BigDecimal repairFee,
            String assignedTo,
            LocalDateTime finishedAt,
            List<RepairLogResponse> logs) {
    }

    public record RepairLogResponse(String action, String content, String operatorName, LocalDateTime createdAt) {
    }

    public record ReturnOrderResponse(
            Long id,
            String returnNo,
            String orderNo,
            Integer type,
            Integer status,
            String reason,
            BigDecimal totalAmount,
            LocalDateTime createdAt,
            List<ReturnItemResponse> items,
            List<RefundRecordResponse> refunds,
            List<RepairResponse> repairs) {
    }

    public record ReturnOrderSummaryResponse(
            Long id,
            String returnNo,
            String orderNo,
            Integer type,
            Integer status,
            String reason,
            BigDecimal totalAmount,
            LocalDateTime createdAt) {
    }
}
