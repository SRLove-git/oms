package com.oms.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.Result;
import com.oms.integration.client.OrderLogisticsClient;
import com.oms.integration.client.OrderLogisticsClient.LogisticsEventRequest;
import com.oms.integration.dto.IntegrationDtos.LogisticsCallbackRequest;
import com.oms.integration.dto.IntegrationDtos.LogisticsResponse;
import com.oms.integration.jtexpress.JtExpressClient;
import com.oms.integration.jtexpress.JtExpressDtos.CancelRequest;
import com.oms.integration.jtexpress.JtExpressDtos.CancelResponse;
import com.oms.integration.jtexpress.JtExpressDtos.ConnoteResponse;
import com.oms.integration.jtexpress.JtExpressDtos.ConnectivityResponse;
import com.oms.integration.jtexpress.JtExpressDtos.CreateShipmentRequest;
import com.oms.integration.jtexpress.JtExpressDtos.CreateShipmentResponse;
import com.oms.integration.jtexpress.JtExpressDtos.StatusResponse;
import com.oms.integration.jtexpress.JtExpressDtos.TrackingResponse;
import com.oms.integration.jtexpress.JtExpressDtos.WebhookPayload;
import com.oms.integration.jtexpress.JtExpressProperties;
import com.oms.integration.jtexpress.JtExpressWebhookVerifier;
import com.oms.integration.service.LogisticsService;
import jakarta.validation.Valid;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/logistics/jtexpress")
public class JtExpressController {

    private static final Logger log = LoggerFactory.getLogger(JtExpressController.class);

    private final JtExpressClient client;
    private final JtExpressProperties properties;
    private final JtExpressWebhookVerifier webhookVerifier;
    private final LogisticsService logisticsService;
    private final ObjectMapper mapper;
    private final OrderLogisticsClient orderLogisticsClient;

    @Value("${oms.internal-service-secret:change-this-internal-secret}")
    private String internalServiceSecret;

    public JtExpressController(
            JtExpressClient client,
            JtExpressProperties properties,
            JtExpressWebhookVerifier webhookVerifier,
            LogisticsService logisticsService,
            ObjectMapper mapper,
            OrderLogisticsClient orderLogisticsClient) {
        this.client = client;
        this.properties = properties;
        this.webhookVerifier = webhookVerifier;
        this.logisticsService = logisticsService;
        this.mapper = mapper;
        this.orderLogisticsClient = orderLogisticsClient;
    }

    @GetMapping("/status")
    public Result<StatusResponse> status() {
        String baseUrl = properties.getBaseUrl();
        String environment = baseUrl != null && baseUrl.contains("demoedishops") ? "staging" : "production";
        return Result.ok(new StatusResponse(
                properties.isEnabled(), properties.isConfigured(), environment, baseUrl));
    }

    @GetMapping("/verify")
    public Result<ConnectivityResponse> verify(
            @RequestParam(defaultValue = "PUDO") String serviceCode) {
        return Result.ok(client.verifyConnectivity(serviceCode));
    }

    @PostMapping("/shipments")
    public Result<CreateShipmentResponse> create(@Valid @RequestBody CreateShipmentRequest request) {
        return Result.ok(client.createShipment(request));
    }

    @GetMapping("/track/{id}")
    public Result<TrackingResponse> track(@PathVariable String id) {
        return Result.ok(client.track(id));
    }

    @PostMapping("/cancel")
    public Result<CancelResponse> cancel(@Valid @RequestBody CancelRequest request) {
        return Result.ok(client.cancel(request.id(), request.reason()));
    }

    @GetMapping("/connote/{id}")
    public Result<ConnoteResponse> connote(
            @PathVariable String id, @RequestParam(defaultValue = "a6") String style) {
        return Result.ok(client.connote(id, style));
    }

    @PostMapping("/webhook")
    public ResponseEntity<Result<LogisticsResponse>> webhook(
            @RequestHeader("X-SimplyPost-Id") String requestId,
            @RequestHeader("X-SimplyPost-Signature") String signature,
            @RequestBody byte[] body) {
        if (!webhookVerifier.verify(requestId, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.fail(ErrorCode.UNAUTHORIZED.getCode(), "J&T webhook 签名无效"));
        }
        WebhookPayload payload;
        try {
            payload = mapper.readValue(body, WebhookPayload.class);
        } catch (Exception ex) {
            return ResponseEntity.badRequest()
                    .body(Result.fail(ErrorCode.BAD_REQUEST.getCode(), "J&T webhook 内容无效"));
        }
        try {
            String status = normalizeStatus(payload.status());
            String trace = String.join(" ",
                    payload.timestamp() == null ? "" : payload.timestamp(),
                    payload.status() == null ? "" : payload.status()).trim();
            LogisticsResponse saved = logisticsService.upsert(new LogisticsCallbackRequest(
                    payload.trackingId(),
                    "J&T Express SG",
                    status,
                    trace,
                    payload.referenceNumber()));
            orderLogisticsClient.notifyLogistics(
                    internalServiceSecret,
                    new LogisticsEventRequest(
                            payload.referenceNumber(),
                            "J&T Express SG",
                            payload.trackingId(),
                            status,
                            trace));
            return ResponseEntity.ok(Result.ok(saved));
        } catch (RuntimeException ex) {
            log.error("J&T webhook processing failed", ex);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.fail(ErrorCode.SERVICE_UNAVAILABLE));
        }
    }

    private static String normalizeStatus(String status) {
        if (status == null) {
            return "unknown";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "PENDING_PICKUP", "ASSIGNED_PICKUP", "ACCEPTED_PICKUP" -> "pending_pickup";
            case "ENROUTE_TO_HUB", "ARRIVED_AT_HUB", "OUT_FOR_DELIVERY",
                    "FORWARDED_TO_SHIPPING_PARTNER", "MERCHANT_DROPPED_OFF" -> "in_transit";
            case "DELIVERED", "COLLECTED" -> "signed";
            case "CANCELLED" -> "cancelled";
            case "RETURNED_TO_SENDER", "RETURNED_TO_HUB", "SCHEDULED_RTS", "OUT_FOR_RTS" -> "returned";
            default -> "exception";
        };
    }
}
