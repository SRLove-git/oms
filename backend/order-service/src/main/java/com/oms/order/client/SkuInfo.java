package com.oms.order.client;

import java.math.BigDecimal;

public record SkuInfo(Long id, String skuNo, String name, BigDecimal price, Integer status) {
}
