package com.oms.aftersales.client;

import com.oms.common.core.result.Result;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "order-service", path = "/api/v1/orders")
public interface OrderClient {

    @GetMapping("/internal/{orderNo}")
    Result<OrderDetail> get(@PathVariable String orderNo);

    @PostMapping("/internal/{orderNo}/after-sales")
    Result<Void> notifyAfterSales(@PathVariable String orderNo, @RequestBody AfterSalesNotifyRequest request);

    @PostMapping("/internal/{orderNo}/after-sales-complete")
    Result<Void> notifyAfterSalesComplete(@PathVariable String orderNo);

    record OrderDetail(
            Long id,
            String orderNo,
            Long merchantId,
            Integer orderType,
            Integer status,
            BigDecimal totalAmount,
            BigDecimal payAmount,
            String currency,
            List<OrderItem> items) {
    }

    record OrderItem(Long id, Long skuId, String skuName, Integer quantity, BigDecimal unitPrice, BigDecimal totalPrice) {
    }

    record AfterSalesNotifyRequest(String returnNo, Integer type, Integer orderStatus) {
    }
}
