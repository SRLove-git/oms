package com.oms.aftersales.controller;

import com.oms.aftersales.dto.AfterSalesDtos.ApplyRequest;
import com.oms.aftersales.dto.AfterSalesDtos.InspectRequest;
import com.oms.aftersales.dto.AfterSalesDtos.RefundRequest;
import com.oms.aftersales.dto.AfterSalesDtos.RepairCreateRequest;
import com.oms.aftersales.dto.AfterSalesDtos.RepairFeeRequest;
import com.oms.aftersales.dto.AfterSalesDtos.RepairProgressRequest;
import com.oms.aftersales.dto.AfterSalesDtos.RepairResponse;
import com.oms.aftersales.dto.AfterSalesDtos.ReviewRequest;
import com.oms.aftersales.dto.AfterSalesDtos.ReturnOrderResponse;
import com.oms.aftersales.dto.AfterSalesDtos.ReturnOrderSummaryResponse;
import com.oms.aftersales.service.AfterSalesService;
import com.oms.common.core.result.PageResult;
import com.oms.common.core.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/return-orders")
public class AfterSalesController {

    private final AfterSalesService afterSalesService;

    public AfterSalesController(AfterSalesService afterSalesService) {
        this.afterSalesService = afterSalesService;
    }

    @PostMapping
    public Result<ReturnOrderResponse> apply(
            @RequestBody ApplyRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Username", required = false) String operatorName) {
        return Result.ok(afterSalesService.apply(request, operatorId, operatorName));
    }

    @GetMapping
    public Result<PageResult<ReturnOrderSummaryResponse>> page(
            @RequestHeader(value = "X-User-Type", required = false) Integer userType,
            @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long filterMerchant = (userType != null && userType == 1) ? null : merchantId;
        return Result.ok(afterSalesService.page(filterMerchant, status, page, size));
    }

    @GetMapping("/{returnNo}")
    public Result<ReturnOrderResponse> get(@PathVariable String returnNo) {
        return Result.ok(afterSalesService.get(returnNo));
    }

    @PostMapping("/{returnNo}/review")
    public Result<Void> review(
            @PathVariable String returnNo,
            @RequestBody ReviewRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Username", required = false) String operatorName) {
        afterSalesService.review(returnNo, request, operatorId, operatorName);
        return Result.ok();
    }

    @PostMapping("/{returnNo}/receive")
    public Result<Void> receive(
            @PathVariable String returnNo,
            @RequestBody InspectRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Username", required = false) String operatorName) {
        afterSalesService.receiveAndInspect(returnNo, request, operatorId, operatorName);
        return Result.ok();
    }

    @PostMapping("/{returnNo}/refund")
    public Result<Void> refund(
            @PathVariable String returnNo,
            @RequestBody RefundRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Username", required = false) String operatorName) {
        afterSalesService.refund(returnNo, request, operatorId, operatorName);
        return Result.ok();
    }

    @PostMapping("/{returnNo}/exchange-ship")
    public Result<Void> exchangeShip(
            @PathVariable String returnNo,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Username", required = false) String operatorName) {
        afterSalesService.exchangeShip(returnNo, operatorId, operatorName);
        return Result.ok();
    }

    @PostMapping("/{returnNo}/cancel")
    public Result<Void> cancel(
            @PathVariable String returnNo,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Username", required = false) String operatorName) {
        afterSalesService.cancel(returnNo, operatorId, operatorName);
        return Result.ok();
    }

    @PostMapping("/{returnNo}/repairs")
    public Result<RepairResponse> createRepair(
            @PathVariable String returnNo,
            @RequestBody RepairCreateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Username", required = false) String operatorName) {
        return Result.ok(afterSalesService.createRepair(returnNo, request, operatorId, operatorName));
    }

    @PostMapping("/repairs/{repairId}/progress")
    public Result<Void> repairProgress(
            @PathVariable Long repairId,
            @RequestBody RepairProgressRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Username", required = false) String operatorName) {
        afterSalesService.repairProgress(repairId, request, operatorId, operatorName);
        return Result.ok();
    }

    @PostMapping("/repairs/{repairId}/fee")
    public Result<Void> repairFee(
            @PathVariable Long repairId,
            @RequestBody RepairFeeRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Username", required = false) String operatorName) {
        afterSalesService.repairFee(repairId, request, operatorId, operatorName);
        return Result.ok();
    }
}
