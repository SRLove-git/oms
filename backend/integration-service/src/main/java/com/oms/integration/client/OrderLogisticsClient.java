package com.oms.integration.client;

import com.oms.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "order-service",
        url = "${oms.clients.order-url:}",
        path = "/api/v1/orders/internal")
public interface OrderLogisticsClient {

    @PostMapping("/logistics-event")
    Result<Void> notifyLogistics(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @RequestBody LogisticsEventRequest request);

    record LogisticsEventRequest(
            String orderNo,
            String carrier,
            String trackingNo,
            String logisticsStatus,
            String trace) {
    }
}
