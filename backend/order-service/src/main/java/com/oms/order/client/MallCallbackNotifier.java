package com.oms.order.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.order.entity.OpenApiEvent;
import com.oms.order.mapper.OpenApiEventMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 商城事件回调入口：只负责把订单状态事件写入 outbox，实际投递由
 * {@link OpenApiCallbackDispatcher} 异步执行并做退避重试。
 */
@Service
public class MallCallbackNotifier {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final int STATUS_PENDING = 0;

    private final OpenApiEventMapper eventMapper;

    @Value("${oms.callback.app-id:oms}")
    private String appId;

    public MallCallbackNotifier(OpenApiEventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    public void notifyOrderStatus(String orderNo, String externalOrderNo, int status, String eventType) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", orderNo);
        data.put("externalOrderNo", externalOrderNo);
        data.put("status", status);
        enqueue(eventType, orderNo, externalOrderNo, data);
    }

    public void notifyShipment(
            String orderNo,
            String externalOrderNo,
            String trackingNo,
            String carrier) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", orderNo);
        data.put("externalOrderNo", externalOrderNo);
        data.put("status", 4);
        data.put("carrier", carrier);
        data.put("trackingNo", trackingNo);
        data.put("logisticsStatus", "SHIPPED");
        enqueue("order.shipped", orderNo, externalOrderNo, data);
    }

    public void notifyLogistics(
            String orderNo,
            String externalOrderNo,
            int status,
            String carrier,
            String trackingNo,
            String logisticsStatus,
            String trace) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", orderNo);
        data.put("externalOrderNo", externalOrderNo);
        data.put("status", status);
        data.put("carrier", carrier);
        data.put("trackingNo", trackingNo);
        data.put("logisticsStatus", logisticsStatus);
        data.put("trace", trace);
        enqueue("logistics.updated", orderNo, externalOrderNo, data);
    }

    private void enqueue(String eventType, String orderNo, String externalOrderNo, Map<String, Object> data) {
        try {
            String eventId = UUID.randomUUID().toString();
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventId", eventId);
            envelope.put("event", eventType);
            envelope.put("appId", appId);
            envelope.put("timestamp", Instant.now().toString());
            envelope.put("data", data);
            String payload = MAPPER.writeValueAsString(envelope);

            OpenApiEvent event = new OpenApiEvent();
            event.setEventId(eventId);
            event.setEventType(eventType);
            event.setOrderNo(orderNo);
            event.setExternalOrderNo(externalOrderNo);
            event.setPayload(payload);
            event.setStatus(STATUS_PENDING);
            event.setRetryCount(0);
            eventMapper.insert(event);
        } catch (Exception ex) {
            throw new IllegalStateException("商城回调事件入队失败", ex);
        }
    }
}
