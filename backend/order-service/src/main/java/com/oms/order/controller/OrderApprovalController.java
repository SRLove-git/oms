package com.oms.order.controller;

import com.oms.common.core.result.Result;
import com.oms.order.dto.OrderDtos.ApprovalRequest;
import com.oms.order.dto.OrderDtos.ApprovalResponse;
import com.oms.order.dto.OrderDtos.ApprovalReviewRequest;
import com.oms.order.service.OrderApprovalService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderApprovalController {

    private final OrderApprovalService approvalService;

    public OrderApprovalController(OrderApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PostMapping("/{orderNo}/approvals")
    public Result<ApprovalResponse> create(
            @PathVariable String orderNo,
            @RequestBody ApprovalRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Username", required = false) String operatorName) {
        return Result.ok(approvalService.create(orderNo, request, operatorId, operatorName));
    }

    @GetMapping("/{orderNo}/approvals")
    public Result<List<ApprovalResponse>> list(@PathVariable String orderNo) {
        return Result.ok(approvalService.list(orderNo));
    }

    @PostMapping("/approvals/{id}/review")
    public Result<ApprovalResponse> review(
            @PathVariable Long id,
            @RequestBody ApprovalReviewRequest request,
            @RequestHeader(value = "X-Username", required = false) String operatorName) {
        return Result.ok(approvalService.review(
                id, request.status(), request.requestPayload(), operatorName));
    }
}
