package com.oms.integration.controller;

import com.oms.common.core.result.Result;
import com.oms.integration.dto.IntegrationDtos.LogisticsCallbackRequest;
import com.oms.integration.dto.IntegrationDtos.LogisticsResponse;
import com.oms.integration.service.LogisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/logistics")
public class LogisticsController {

    private final LogisticsService logisticsService;

    public LogisticsController(LogisticsService logisticsService) {
        this.logisticsService = logisticsService;
    }

    @PostMapping("/callback")
    public Result<LogisticsResponse> callback(@RequestBody LogisticsCallbackRequest request) {
        return Result.ok(logisticsService.upsert(request));
    }

    @GetMapping("/{carrier}/{trackingNo}")
    public Result<LogisticsResponse> get(
            @PathVariable String carrier, @PathVariable String trackingNo) {
        return Result.ok(logisticsService.get(trackingNo, carrier));
    }

    @GetMapping("/by-order/{orderNo}")
    public Result<LogisticsResponse> getByOrderNo(@PathVariable String orderNo) {
        return Result.ok(logisticsService.getByOrderNo(orderNo));
    }
}
