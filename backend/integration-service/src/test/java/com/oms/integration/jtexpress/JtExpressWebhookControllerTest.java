package com.oms.integration.jtexpress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.integration.client.OrderLogisticsClient;
import com.oms.integration.controller.JtExpressController;
import com.oms.integration.service.LogisticsService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class JtExpressWebhookControllerTest {

    private JtExpressWebhookVerifier verifier;
    private LogisticsService logisticsService;
    private OrderLogisticsClient orderLogisticsClient;
    private JtExpressController controller;

    @BeforeEach
    void setUp() {
        verifier = mock(JtExpressWebhookVerifier.class);
        logisticsService = mock(LogisticsService.class);
        orderLogisticsClient = mock(OrderLogisticsClient.class);
        controller = new JtExpressController(
                mock(JtExpressClient.class),
                new JtExpressProperties(),
                verifier,
                logisticsService,
                new ObjectMapper(),
                orderLogisticsClient);
    }

    @Test
    void rejectsInvalidSignatureWithHttp401() {
        when(verifier.verify("request-1", "invalid")).thenReturn(false);

        var response = controller.webhook("request-1", "invalid", "{}".getBytes(StandardCharsets.UTF_8));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(401);
        verifyNoInteractions(logisticsService, orderLogisticsClient);
    }

    @Test
    void returnsHttp503SoProviderCanRetryWhenDownstreamFails() {
        when(verifier.verify("request-2", "valid")).thenReturn(true);
        when(logisticsService.upsert(any())).thenThrow(new IllegalStateException("database unavailable"));
        byte[] body = ("{\"status\":\"DELIVERED\",\"tracking_id\":\"JT1\","
                        + "\"reference_number\":\"ORDER1\",\"timestamp\":\"2026-10-26T15:28:00+08:00\"}")
                .getBytes(StandardCharsets.UTF_8);

        var response = controller.webhook("request-2", "valid", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(503);
        verifyNoInteractions(orderLogisticsClient);
    }
}
