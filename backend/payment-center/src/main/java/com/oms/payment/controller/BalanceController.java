package com.oms.payment.controller;

import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.PageResult;
import com.oms.common.core.result.Result;
import com.oms.payment.dto.BalanceDtos.BalanceResponse;
import com.oms.payment.dto.BalanceDtos.BalanceTransactionResponse;
import com.oms.payment.dto.BalanceDtos.RechargeRequest;
import com.oms.payment.service.BalanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/balances")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping
    public Result<BalanceResponse> get(
            @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId) {
        return Result.ok(balanceService.get(merchantId));
    }

    @PostMapping("/recharge")
    public Result<BalanceResponse> recharge(
            @RequestBody RechargeRequest request,
            @RequestHeader(value = "X-User-Type", required = false) Integer userType) {
        if (userType == null || userType != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return Result.ok(balanceService.recharge(request));
    }

    @GetMapping("/transactions")
    public Result<PageResult<BalanceTransactionResponse>> transactions(
            @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(balanceService.page(merchantId, page, size));
    }
}
