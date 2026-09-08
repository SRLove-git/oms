package com.oms.order.client;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.oms.common.core.security.HmacSigner;
import com.oms.order.entity.OpenApiEvent;
import com.oms.order.mapper.OpenApiEventMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** 定时扫描商城回调 outbox，按指数退避重试投递，超限转死信。 */
@Component
public class OpenApiCallbackDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OpenApiCallbackDispatcher.class);
    private static final String CALLBACK_PATH = "/api/v1/callback/oms";
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_DELIVERED = 1;
    private static final int STATUS_FAILED = 2;
    private static final long[] BACKOFF_SECONDS = {1, 5, 30, 300, 1800};

    private final OpenApiEventMapper eventMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${oms.callback.base-url:http://localhost:8090}")
    private String baseUrl;

    @Value("${oms.callback.secret:demo-mall-callback-secret-change-me}")
    private String secret;

    @Value("${oms.callback.app-id:oms}")
    private String appId;

    @Value("${oms.callback.max-retries:5}")
    private int maxRetries;

    @Value("${oms.callback.batch-size:100}")
    private int batchSize;

    public OpenApiCallbackDispatcher(OpenApiEventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    @Scheduled(
            initialDelayString = "15000",
            fixedDelayString = "${oms.callback.scan-interval-ms:5000}")
    public void dispatch() {
        List<OpenApiEvent> due =
                eventMapper.selectList(
                        new LambdaQueryWrapper<OpenApiEvent>()
                                .eq(OpenApiEvent::getStatus, STATUS_PENDING)
                                .and(
                                        wrapper ->
                                                wrapper
                                                        .isNull(OpenApiEvent::getNextRetryAt)
                                                        .or()
                                                        .le(
                                                                OpenApiEvent::getNextRetryAt,
                                                                LocalDateTime.now()))
                                .orderByAsc(OpenApiEvent::getId)
                                .last("LIMIT " + Math.max(1, batchSize)));
        for (OpenApiEvent event : due) {
            deliver(event);
        }
    }

    private void deliver(OpenApiEvent event) {
        try {
            post(event.getPayload());
            event.setStatus(STATUS_DELIVERED);
            event.setNextRetryAt(null);
            event.setLastError(null);
            persist(event);
            log.info(
                    "商城回调投递成功 eventId={} event={} orderNo={}",
                    event.getEventId(),
                    event.getEventType(),
                    event.getOrderNo());
        } catch (Exception ex) {
            int retry = event.getRetryCount() == null ? 1 : event.getRetryCount() + 1;
            event.setRetryCount(retry);
            event.setLastError(truncate(ex.getMessage()));
            if (retry > maxRetries) {
                event.setStatus(STATUS_FAILED);
                event.setNextRetryAt(null);
                log.error(
                        "商城回调进入死信 eventId={} event={} retry={} error={}",
                        event.getEventId(),
                        event.getEventType(),
                        retry,
                        ex.getMessage());
            } else {
                event.setStatus(STATUS_PENDING);
                event.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds(retry)));
                log.warn(
                        "商城回调投递失败，稍后重试 eventId={} event={} retry={} error={}",
                        event.getEventId(),
                        event.getEventType(),
                        retry,
                        ex.getMessage());
            }
            persist(event);
        }
    }

    private void post(String payload) {
        String timestamp = HmacSigner.timestamp();
        String nonce = HmacSigner.nonce();
        String sign =
                HmacSigner.sign(
                        secret,
                        "POST",
                        CALLBACK_PATH,
                        timestamp,
                        nonce,
                        HmacSigner.sha256Hex(payload));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/json;charset=UTF-8"));
        headers.set("X-App-Id", appId);
        headers.set("X-Timestamp", timestamp);
        headers.set("X-Nonce", nonce);
        headers.set("X-Sign", sign);
        restTemplate.postForEntity(
                baseUrl + CALLBACK_PATH, new HttpEntity<>(payload, headers), String.class);
    }

    private void persist(OpenApiEvent event) {
        eventMapper.update(
                null,
                new LambdaUpdateWrapper<OpenApiEvent>()
                        .eq(OpenApiEvent::getId, event.getId())
                        .set(OpenApiEvent::getStatus, event.getStatus())
                        .set(OpenApiEvent::getRetryCount, event.getRetryCount())
                        .set(OpenApiEvent::getNextRetryAt, event.getNextRetryAt())
                        .set(OpenApiEvent::getLastError, event.getLastError()));
    }

    private static long backoffSeconds(int retry) {
        return BACKOFF_SECONDS[Math.min(Math.max(retry, 1), BACKOFF_SECONDS.length) - 1];
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
