package com.oms.aftersales.client;

import com.oms.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "integration-service", path = "/api/v1")
public interface IntegrationClient {

    @PostMapping("/notifications/send")
    Result<Void> send(@RequestBody SendRequest request);

    record SendRequest(String channel, String scene, String receiver, String title, String content) {
    }
}
