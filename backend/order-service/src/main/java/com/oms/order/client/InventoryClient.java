package com.oms.order.client;

import com.oms.common.core.result.Result;
import com.oms.order.dto.OrderDtos.OrderItemRequest;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "inventory-service", path = "/api/v1")
public interface InventoryClient {

    @GetMapping("/skus/{id}")
    Result<SkuInfo> getSku(@PathVariable Long id);

    @PostMapping("/inventories/reserve")
    Result<Void> reserve(@RequestBody StockRequest request);

    @PostMapping("/inventories/release")
    Result<Void> release(@RequestBody StockRequest request);

    @PostMapping("/inventories/deduct")
    Result<Void> deduct(@RequestBody StockRequest request);

    @PostMapping("/inventories/restore")
    Result<Void> restore(@RequestBody StockRequest request);

    record StockRequest(String orderNo, List<OrderItemRequest> items) {
    }
}
