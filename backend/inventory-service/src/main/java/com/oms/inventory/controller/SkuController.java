package com.oms.inventory.controller;

import com.oms.common.core.result.PageResult;
import com.oms.common.core.result.Result;
import com.oms.inventory.dto.SkuDtos.SkuCreateRequest;
import com.oms.inventory.dto.SkuDtos.SkuResponse;
import com.oms.inventory.dto.SkuDtos.SkuStatusRequest;
import com.oms.inventory.service.SkuService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/skus")
public class SkuController {

    private final SkuService skuService;

    public SkuController(SkuService skuService) {
        this.skuService = skuService;
    }

    @PostMapping
    public Result<Long> create(@RequestBody SkuCreateRequest request) {
        return Result.ok(skuService.createSku(request));
    }

    @GetMapping
    public Result<PageResult<SkuResponse>> page(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(skuService.page(keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<SkuResponse> get(@PathVariable Long id) {
        return Result.ok(skuService.get(id));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody SkuStatusRequest request) {
        skuService.updateStatus(id, request.status());
        return Result.ok();
    }
}
