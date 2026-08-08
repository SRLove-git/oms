package com.oms.inventory.controller;

import com.oms.common.core.result.PageResult;
import com.oms.common.core.result.Result;
import com.oms.inventory.dto.InventoryDtos.InboundRequest;
import com.oms.inventory.dto.InventoryDtos.InventoryResponse;
import com.oms.inventory.dto.InventoryDtos.ReserveRequest;
import com.oms.inventory.service.InventoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventories")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/inbound")
    public Result<Void> inbound(
            @RequestBody InboundRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId) {
        inventoryService.inbound(request, operatorId);
        return Result.ok();
    }

    @PostMapping("/reserve")
    public Result<Void> reserve(
            @RequestBody ReserveRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId) {
        inventoryService.reserve(request, operatorId);
        return Result.ok();
    }

    @PostMapping("/release")
    public Result<Void> release(
            @RequestBody ReserveRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId) {
        inventoryService.release(request, operatorId);
        return Result.ok();
    }

    @PostMapping("/deduct")
    public Result<Void> deduct(
            @RequestBody ReserveRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId) {
        inventoryService.deduct(request, operatorId);
        return Result.ok();
    }

    @PostMapping("/restore")
    public Result<Void> restore(
            @RequestBody ReserveRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId) {
        inventoryService.restore(request, operatorId);
        return Result.ok();
    }

    @GetMapping
    public Result<PageResult<InventoryResponse>> page(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long skuId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(inventoryService.page(warehouseId, skuId, page, size));
    }
}
