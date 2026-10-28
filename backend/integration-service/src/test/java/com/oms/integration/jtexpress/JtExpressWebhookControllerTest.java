package com.oms.integration.jtexpress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.common.core.exception.BusinessException;
import com.oms.integration.client.OrderLogisticsClient;
import com.oms.integration.controller.JtExpressController;
import com.oms.integration.dto.IntegrationDtos.LogisticsCallbackRequest;
import com.oms.integration.dto.IntegrationDtos.LogisticsResponse;
import com.oms.integration.jtexpress.JtExpressDtos.CancelRequest;
import com.oms.integration.jtexpress.JtExpressDtos.CancelResponse;
import com.oms.integration.jtexpress.JtExpressDtos.ContactDetails;
import com.oms.integration.jtexpress.JtExpressDtos.CreateShipmentRequest;
import com.oms.integration.jtexpress.JtExpressDtos.CreateShipmentResponse;
import com.oms.integration.jtexpress.JtExpressDtos.ItemDetails;
import com.oms.integration.service.LogisticsService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class JtExpressWebhookControllerTest {

    private JtExpressClient client;
    private JtExpressWebhookVerifier verifier;
    private LogisticsService logisticsService;
    private OrderLogisticsClient orderLogisticsClient;
    private JtExpressController controller;

    @BeforeEach
    void setUp() {
        client = mock(JtExpressClient.class);
        verifier = mock(JtExpressWebhookVerifier.class);
        logisticsService = mock(LogisticsService.class);
        orderLogisticsClient = mock(OrderLogisticsClient.class);
        controller = new JtExpressController(
                client,
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
    void rejectsMissingSignatureHeadersWithHttp401() {
        var response = controller.webhook(null, null, "{}".getBytes(StandardCharsets.UTF_8));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(401);
        verify(verifier).verify(null, null);
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

    @Test
    void rejectsCreateForNonAdmin() {
        assertThatThrownBy(() -> controller.create(shipmentRequest(), 2))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(client, logisticsService);
    }

    @Test
    void createPersistsShipmentForTrackingAndLabel() {
        when(logisticsService.findLatestByOrderNo("ORDER-1")).thenReturn(null);
        when(client.createShipment(any()))
                .thenReturn(new CreateShipmentResponse("JT1", "ORDER-1", "18A"));

        var result = controller.create(shipmentRequest(), 1);

        assertThat(result.data().trackingId()).isEqualTo("JT1");
        verify(logisticsService).upsert(any());
    }

    @Test
    void createReusesExistingShipmentOnRetry() {
        when(logisticsService.findLatestByOrderNo("ORDER-1"))
                .thenReturn(new LogisticsResponse(
                        1L, "ORDER-1", "JT0", "J&T Express SG", "created", List.of(), null));

        var result = controller.create(shipmentRequest(), 1);

        assertThat(result.data().trackingId()).isEqualTo("JT0");
        verifyNoInteractions(client);
    }

    @Test
    void webhookKeepsFailureReasonAndPodInTrace() {
        when(verifier.verify("request-3", "valid")).thenReturn(true);
        when(logisticsService.upsert(any())).thenAnswer(invocation -> {
            LogisticsCallbackRequest request = invocation.getArgument(0);
            return new LogisticsResponse(
                    1L,
                    request.orderNo(),
                    request.trackingNo(),
                    request.carrier(),
                    request.status(),
                    List.of(request.trace()),
                    null);
        });
        byte[] body = ("{\"status\":\"FAILED_DELIVERY\",\"tracking_id\":\"JT1\","
                        + "\"reference_number\":\"ORDER1\",\"comments\":\"Consignee not available\","
                        + "\"no_of_attempts\":2,\"pod_url\":\"https://example.com/pod.jpg\"}")
                .getBytes(StandardCharsets.UTF_8);

        var response = controller.webhook("request-3", "valid", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArgumentCaptor<LogisticsCallbackRequest> captor =
                ArgumentCaptor.forClass(LogisticsCallbackRequest.class);
        verify(logisticsService).upsert(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("exception");
        assertThat(captor.getValue().trace())
                .contains("Consignee not available")
                .contains("attempts=2")
                .contains("https://example.com/pod.jpg");
    }

    @Test
    void cancelFailsWhenProviderReturnsNoSuccess() {
        when(client.cancel("JT1", "reason")).thenReturn(new CancelResponse(List.of(), List.of()));

        assertThatThrownBy(() -> controller.cancel(new CancelRequest("JT1", "reason"), 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("取消运单失败");
    }

    private static CreateShipmentRequest shipmentRequest() {
        return new CreateShipmentRequest(
                "ORDER-1",
                "DOM123",
                null,
                null,
                new ContactDetails("Jane", "+6591234567", null, "1 Marina Boulevard", "018956", null),
                List.of(new ItemDetails(null, null, null, BigDecimal.valueOf(500), "G", "Medical item", 1)));
    }
}
