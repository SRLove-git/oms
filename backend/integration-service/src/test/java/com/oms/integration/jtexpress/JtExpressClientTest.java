package com.oms.integration.jtexpress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.integration.jtexpress.JtExpressDtos.ContactDetails;
import com.oms.integration.jtexpress.JtExpressDtos.CreateShipmentRequest;
import com.oms.integration.jtexpress.JtExpressDtos.CreateShipmentResponse;
import com.oms.integration.jtexpress.JtExpressDtos.ItemDetails;
import com.oms.integration.jtexpress.JtExpressDtos.Money;
import com.oms.integration.jtexpress.JtExpressDtos.PickupDetails;
import com.oms.integration.jtexpress.JtExpressDtos.TimeWindow;
import com.oms.integration.jtexpress.JtExpressDtos.TrackingResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JtExpressClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private JtExpressProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        properties = new JtExpressProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setEmail("api@example.com");
        properties.setPassword("secret");
        properties.setMerchantCode("MERCHANT-1");
        properties.setServiceCode("DOM123");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void createsShipmentAndCachesAccessToken() throws Exception {
        AtomicInteger loginCalls = new AtomicInteger();
        AtomicInteger shipmentCalls = new AtomicInteger();
        server.createContext("/api/gateway/v1/auth/login", exchange -> {
            loginCalls.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("Authorization"))
                    .isEqualTo("Basic YXBpQGV4YW1wbGUuY29tOnNlY3JldA==");
            respond(exchange, 200, "{\"access_token\":\"jwt-token\"}");
        });
        server.createContext("/api/gateway/v1/deliveries", exchange -> {
            shipmentCalls.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("JWT jwt-token");
            JsonNode body = mapper.readTree(exchange.getRequestBody());
            assertThat(body.path("service_code").asText()).isEqualTo("DOM123");
            assertThat(body.path("merchant_code").asText()).isEqualTo("MERCHANT-1");
            assertThat(body.path("consignee_details").path("postcode").asText()).isEqualTo("018956");
            assertThat(body.path("consignee_details").path("country_code").asText()).isEqualTo("SG");
            assertThat(body.path("pickup_details").path("time_window").path("from").asText())
                    .isEqualTo("9");
            assertThat(body.path("cod").path("currency").asText()).isEqualTo("SGD");
            respond(exchange, 200,
                    "{\"tracking_id\":\"JT123\",\"reference_number\":\"ORDER-1\",\"sorting_code\":\"18A\"}");
        });
        server.createContext("/api/gateway/v1/deliveries/connote/JT123/A6/S1", exchange ->
                respond(exchange, 200,
                        "{\"reference_number\":\"ORDER-1\",\"connote_label_url\":\"https://example.test/a6.pdf\"}"));
        server.createContext("/api/gateway/v1/distribution-points", exchange -> {
            assertThat(exchange.getRequestURI().getQuery()).isEqualTo("service_code=PUDO");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("JWT jwt-token");
            respond(exchange, 200, "[{\"name\":\"Point A\"},{\"name\":\"Point B\"}]");
        });
        server.start();

        JtExpressClient client = new JtExpressClient(properties, mapper, HttpClient.newHttpClient());
        CreateShipmentRequest request = new CreateShipmentRequest(
                "ORDER-1",
                "",
                null,
                null,
                null,
                new ContactDetails("Jane", "+6591234567", null, "1 Marina Boulevard", "018956", null),
                new PickupDetails(
                        "Warehouse",
                        "+6511111111",
                        "ops@example.com",
                        "24 Penjuru Road",
                        "#01-02",
                        "609128",
                        "SG",
                        "2026-10-29",
                        new TimeWindow("9", "18")),
                List.of(new ItemDetails(null, null, null, BigDecimal.valueOf(500), "G", "Medical item", 1)),
                new Money(BigDecimal.valueOf(20), "SGD"),
                new Money(BigDecimal.valueOf(20), "SGD"));

        CreateShipmentResponse first = client.createShipment(request);
        CreateShipmentResponse second = client.createShipment(request);
        var connectivity = client.verifyConnectivity("PUDO");
        var connote = client.connote("JT123", "a6");

        assertThat(first.trackingId()).isEqualTo("JT123");
        assertThat(second.trackingId()).isEqualTo("JT123");
        assertThat(loginCalls).hasValue(1);
        assertThat(shipmentCalls).hasValue(2);
        assertThat(connectivity.connected()).isTrue();
        assertThat(connectivity.distributionPointCount()).isEqualTo(2);
        assertThat(connote.connoteLabelUrl()).isEqualTo("https://example.test/a6.pdf");
    }

    @Test
    void surfacesBusinessErrorEvenWhenHttpStatusIs200() throws Exception {
        server.createContext("/api/gateway/v1/auth/login", exchange ->
                respond(exchange, 200, "{\"token\":\"jwt-token\"}"));
        server.createContext("/api/gateway/v1/deliveries/operation", exchange ->
                respond(exchange, 200, "{\"status\":400,\"message\":\"not cancellable\"}"));
        server.start();

        JtExpressClient client = new JtExpressClient(properties, mapper, HttpClient.newHttpClient());

        assertThatThrownBy(() -> client.cancel("JT123", "customer request"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not cancellable");
    }

    @Test
    void omitsBlankMerchantCodeAndNormalizesWeightUnit() throws Exception {
        properties.setMerchantCode("");
        server.createContext("/api/gateway/v1/auth/login", exchange ->
                respond(exchange, 200, "{\"token\":\"jwt-token\"}"));
        server.createContext("/api/gateway/v1/deliveries", exchange -> {
            JsonNode body = mapper.readTree(exchange.getRequestBody());
            assertThat(body.has("merchant_code")).isFalse();
            assertThat(body.path("item_details").get(0).path("weight_unit").asText()).isEqualTo("Kg");
            respond(exchange, 200, "{\"tracking_id\":\"JT123\",\"reference_number\":\"ORDER-1\"}");
        });
        server.start();

        JtExpressClient client = new JtExpressClient(properties, mapper, HttpClient.newHttpClient());
        CreateShipmentRequest request = new CreateShipmentRequest(
                "ORDER-1",
                null,
                null,
                null,
                new ContactDetails("Jane", "+6591234567", null, "1 Marina Boulevard", "018956", null),
                List.of(new ItemDetails(null, null, null, BigDecimal.ONE, "KG", "Medical item", 1)));

        assertThat(client.createShipment(request).trackingId()).isEqualTo("JT123");
    }

    @Test
    void trackIgnoresProviderSpecificActivityFields() throws Exception {
        server.createContext("/api/gateway/v1/auth/login", exchange ->
                respond(exchange, 200, "{\"token\":\"jwt-token\"}"));
        server.createContext("/api/gateway/v1/track/JT123", exchange ->
                respond(exchange, 200, """
                        {
                          "tracking_id":"JT123",
                          "reference_number":"ORDER-1",
                          "status":"PENDING_PICKUP",
                          "activities":[{
                            "name":"PENDING_PICKUP",
                            "notes":"JTS-API",
                            "created_at":"2026-10-30T16:48:58+00:00",
                            "picList":[]
                          }]
                        }
                        """));
        server.start();

        JtExpressClient client = new JtExpressClient(properties, mapper, HttpClient.newHttpClient());
        TrackingResponse response = client.track("JT123");

        assertThat(response.status()).isEqualTo("PENDING_PICKUP");
        assertThat(response.activities()).hasSize(1);
        assertThat(response.activities().getFirst().name()).isEqualTo("PENDING_PICKUP");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
