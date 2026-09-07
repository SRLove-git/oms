package com.oms.inventory.controller;

import com.oms.common.core.result.Result;
import com.oms.inventory.dto.InventoryDtos.StockTakeRequest;
import com.oms.inventory.dto.InventoryDtos.StockTakeResponse;
import com.oms.inventory.dto.InventoryDtos.WaveCreateRequest;
import com.oms.inventory.dto.InventoryDtos.WaveResponse;
import com.oms.inventory.dto.InventoryDtos.WaveStatusRequest;
import com.oms.inventory.service.StockTakeService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StockTakeController {

    private final StockTakeService stockTakeService;

    public StockTakeController(StockTakeService stockTakeService) {
        this.stockTakeService = stockTakeService;
    }

    @PostMapping("/stock-take")
    public Result<StockTakeResponse> create(
            @RequestBody StockTakeRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId) {
        return Result.ok(stockTakeService.create(request, operatorId));
    }

    @GetMapping("/stock-take")
    public Result<List<StockTakeResponse>> list(@RequestParam(required = false) Long warehouseId) {
        return Result.ok(stockTakeService.list(warehouseId));
    }

    @PostMapping("/stock-take/{id}/review")
    public Result<Void> review(@PathVariable Long id) {
        stockTakeService.review(id);
        return Result.ok();
    }

    @PostMapping("/waves")
    public Result<WaveResponse> createWave(
            @RequestBody WaveCreateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId) {
        return Result.ok(stockTakeService.createWave(request, operatorId));
    }

    @PostMapping("/waves/{id}/status")
    public Result<WaveResponse> updateWave(@PathVariable Long id, @RequestBody WaveStatusRequest request) {
        return Result.ok(stockTakeService.updateWave(id, request));
    }

    @GetMapping("/waves")
    public Result<List<WaveResponse>> listWaves() {
        return Result.ok(stockTakeService.listWaves());
    }
}
