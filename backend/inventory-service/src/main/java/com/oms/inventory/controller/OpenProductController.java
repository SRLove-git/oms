package com.oms.inventory.controller;

import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.PageResult;
import com.oms.common.core.result.Result;
import com.oms.inventory.dto.OpenProductDtos.OpenSkuResponse;
import com.oms.inventory.dto.OpenProductDtos.StockResponse;
import com.oms.inventory.dto.SkuDtos.SkuResponse;
import com.oms.inventory.service.InventoryService;
import com.oms.inventory.service.SkuService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商城开放 API：在售商品与实时库存查询（经网关签名校验，见网关 OpenApiAuthFilter）。
 */
@RestController
@RequestMapping("/api/v1/open")
public class OpenProductController {

    private final SkuService skuService;
    private final InventoryService inventoryService;

    public OpenProductController(SkuService skuService, InventoryService inventoryService) {
        this.skuService = skuService;
        this.inventoryService = inventoryService;
    }

    @GetMapping("/products")
    public Result<PageResult<OpenSkuResponse>> products(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResult<SkuResponse> result = skuService.pageOnSale(keyword, page, size);
        List<Long> skuIds = result.records().stream().map(SkuResponse::id).toList();
        Map<Long, Integer> stocks = inventoryService.availableStocks(skuIds);
        return Result.ok(PageResult.of(
                result.total(),
                result.records().stream()
                        .map(sku -> new OpenSkuResponse(
                                sku.id(),
                                sku.skuNo(),
                                sku.spuNo(),
                                sku.name(),
                                sku.spec(),
                                sku.registrationNo(),
                                sku.udi(),
                                sku.price(),
                                sku.status(),
                                stocks.getOrDefault(sku.id(), 0)))
                        .toList()));
    }

    @GetMapping("/products/{skuId}")
    public Result<OpenSkuResponse> product(@PathVariable Long skuId) {
        SkuResponse sku = requireOnSale(skuId);
        return Result.ok(new OpenSkuResponse(
                sku.id(),
                sku.skuNo(),
                sku.spuNo(),
                sku.name(),
                sku.spec(),
                sku.registrationNo(),
                sku.udi(),
                sku.price(),
                sku.status(),
                inventoryService.availableStock(skuId)));
    }

    @GetMapping("/skus/{skuId}/stock")
    public Result<StockResponse> stock(@PathVariable Long skuId) {
        SkuResponse sku = requireOnSale(skuId);
        return Result.ok(new StockResponse(skuId, sku.skuNo(), inventoryService.availableStock(skuId)));
    }

    private SkuResponse requireOnSale(Long skuId) {
        SkuResponse sku = skuService.get(skuId);
        if (sku.status() == null || sku.status() != 1) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "SKU 已下架");
        }
        return sku;
    }
}
