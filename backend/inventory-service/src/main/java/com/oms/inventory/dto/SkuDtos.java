package com.oms.inventory.dto;

import java.math.BigDecimal;

public final class SkuDtos {

    private SkuDtos() {
    }

    public record SkuCreateRequest(
            String spuNo,
            String spuName,
            String skuNo,
            String name,
            String spec,
            String barcode,
            String udi,
            String registrationNo,
            BigDecimal price,
            BigDecimal costPrice) {
    }

    public record SkuResponse(
            Long id,
            Long spuId,
            String spuNo,
            String skuNo,
            String name,
            String spec,
            String barcode,
            String udi,
            String registrationNo,
            BigDecimal price,
            BigDecimal costPrice,
            Integer status) {
    }

    public record SkuStatusRequest(Integer status) {
    }
}
