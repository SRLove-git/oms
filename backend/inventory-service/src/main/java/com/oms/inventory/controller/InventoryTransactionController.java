package com.oms.inventory.controller;

import com.oms.common.core.result.PageResult;
import com.oms.common.core.result.Result;
import com.oms.inventory.dto.InventoryDtos.TransactionResponse;
import com.oms.inventory.service.InventoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory-transactions")
public class InventoryTransactionController {

    private final InventoryService inventoryService;

    public InventoryTransactionController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public Result<PageResult<TransactionResponse>> page(
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) String bizNo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(inventoryService.pageTransactions(skuId, bizNo, page, size));
    }
}
