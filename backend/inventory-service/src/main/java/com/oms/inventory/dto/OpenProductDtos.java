package com.oms.inventory.dto;

import java.math.BigDecimal;

/**
 * 商城开放 API 商品 DTO（面向外部商城调用方）。
 */
public final class OpenProductDtos {

    private OpenProductDtos() {
    }

    public record OpenSkuResponse(
            Long skuId,
            String skuNo,
            String spuNo,
            String name,
            String spec,
            String registrationNo,
            String udi,
            BigDecimal price,
            Integer status,
            int availableStock) {
    }

    public record StockResponse(Long skuId, String skuNo, int availableStock) {
    }
}
