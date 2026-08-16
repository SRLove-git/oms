package com.oms.aftersales.client;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 商城事件回调：售后状态变化后通知 BUZUD 商城。
 */
@Service
public class MallCallbackNotifier {

    private static final Logger log = LoggerFactory.getLogger(MallCallbackNotifier.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${oms.callback.base-url:http://localhost:8090}")
    private String baseUrl;

    public void notifyAfterSaleStatus(
            String orderNo, String returnNo, Integer afterSaleStatus, String eventType) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventType", eventType);
        body.put("orderNo", orderNo);
        body.put("returnNo", returnNo);
        body.put("afterSaleStatus", afterSaleStatus);
        try {
            restTemplate.postForEntity(baseUrl + "/api/v1/callback/oms", body, Void.class);
        } catch (Exception ex) {
            log.warn(
                    "商城售后回调发送失败 eventType={} returnNo={} error={}",
                    eventType,
                    returnNo,
                    ex.getMessage());
        }
    }
}
