package com.oms.aftersales.client;

import com.oms.common.core.result.Result;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "inventory-service", path = "/api/v1")
public interface InventoryClient {

    @PostMapping("/inventories/restore")
    Result<Void> restore(@RequestBody StockRequest request);

    record StockRequest(String orderNo, List<Item> items) {
    }

    record Item(Long skuId, int quantity) {
    }
}
