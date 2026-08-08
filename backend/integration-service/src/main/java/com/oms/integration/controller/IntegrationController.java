package com.oms.integration.controller;

import com.oms.common.core.result.PageResult;
import com.oms.common.core.result.Result;
import com.oms.integration.dto.IntegrationDtos.ExternalOrderMappingResponse;
import com.oms.integration.dto.IntegrationDtos.ExternalOrderPullRequest;
import com.oms.integration.dto.IntegrationDtos.SyncAfterSalesRequest;
import com.oms.integration.dto.IntegrationDtos.SyncShipmentRequest;
import com.oms.integration.dto.IntegrationDtos.SyncStockRequest;
import com.oms.integration.service.IntegrationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationController {

    private final IntegrationService integrationService;

    public IntegrationController(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @PostMapping("/orders/pull")
    public Result<ExternalOrderMappingResponse> pullOrder(@RequestBody ExternalOrderPullRequest request) {
        return Result.ok(integrationService.pullOrder(request));
    }

    @GetMapping("/orders")
    public Result<PageResult<ExternalOrderMappingResponse>> pageMappings(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(integrationService.pageMappings(platform, status, page, size));
    }

    @PostMapping("/orders/ship-sync")
    public Result<Void> syncShipment(@RequestBody SyncShipmentRequest request) {
        integrationService.syncShipment(request);
        return Result.ok();
    }

    @PostMapping("/orders/after-sales-sync")
    public Result<Void> syncAfterSales(@RequestBody SyncAfterSalesRequest request) {
        integrationService.syncAfterSales(request);
        return Result.ok();
    }

    @PostMapping("/stock-sync")
    public Result<Void> syncStock(@RequestBody SyncStockRequest request) {
        integrationService.syncStock(request);
        return Result.ok();
    }
}
