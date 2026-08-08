package com.oms.inventory.controller;

import com.oms.common.core.result.Result;
import com.oms.inventory.dto.WarehouseDtos.WarehouseCreateRequest;
import com.oms.inventory.dto.WarehouseDtos.WarehouseResponse;
import com.oms.inventory.service.WarehouseService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @PostMapping
    public Result<Long> create(@RequestBody WarehouseCreateRequest request) {
        return Result.ok(warehouseService.create(request));
    }

    @GetMapping
    public Result<List<WarehouseResponse>> list() {
        return Result.ok(warehouseService.list());
    }
}
