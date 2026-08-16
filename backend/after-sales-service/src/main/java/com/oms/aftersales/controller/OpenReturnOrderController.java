package com.oms.aftersales.controller;

import com.oms.aftersales.dto.AfterSalesDtos.ReturnOrderResponse;
import com.oms.aftersales.dto.OpenAfterSalesDtos.OpenReturnOrderRequest;
import com.oms.aftersales.dto.OpenAfterSalesDtos.OpenReturnOrderResponse;
import com.oms.aftersales.service.AfterSalesService;
import com.oms.common.core.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商城开放 API：商城用户发起退款/售后申请，由网关签名校验并注入商户 ID。
 */
@RestController
@RequestMapping("/api/v1/open/return-orders")
public class OpenReturnOrderController {

    private final AfterSalesService afterSalesService;

    public OpenReturnOrderController(AfterSalesService afterSalesService) {
        this.afterSalesService = afterSalesService;
    }

    @PostMapping
    public Result<ReturnOrderResponse> apply(
            @RequestBody OpenReturnOrderRequest request,
            @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId) {
        return Result.ok(afterSalesService.applyOpen(request, merchantId));
    }

    @GetMapping("/by-external/{externalOrderNo}")
    public Result<OpenReturnOrderResponse> getByExternal(
            @PathVariable String externalOrderNo,
            @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId) {
        return Result.ok(afterSalesService.getOpenByExternal(externalOrderNo, merchantId));
    }
}
