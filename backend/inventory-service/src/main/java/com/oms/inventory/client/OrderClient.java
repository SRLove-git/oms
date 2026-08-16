package com.oms.inventory.client;

import com.oms.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "order-service", path = "/api/v1/orders/internal")
public interface OrderClient {

    @GetMapping("/skus/{skuId}/references")
    Result<SkuReferenceCheck> skuReferences(@PathVariable Long skuId);

    record SkuReferenceCheck(boolean hasOrders, long activeCount, long archivedCount) {
    }
}
