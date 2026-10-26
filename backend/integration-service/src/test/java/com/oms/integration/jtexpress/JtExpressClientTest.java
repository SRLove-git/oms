package com.oms.integration.jtexpress;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.integration.jtexpress.JtExpressDtos.ContactDetails;
import com.oms.integration.jtexpress.JtExpressDtos.CreateShipmentRequest;
import com.oms.integration.jtexpress.JtExpressDtos.CreateShipmentResponse;
import com.oms.integration.jtexpress.JtExpressDtos.ItemDetails;
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
            respond(exchange, 200,
                    "{\"tracking_id\":\"JT123\",\"reference_number\":\"ORDER-1\",\"sorting_code\":\"18A\"}");
        });
        server.createContext("/api/gateway/v1/distribution-points", exchange -> {
            assertThat(exchange.getRequestURI().getQuery()).isEqualTo("service_code=PUDO");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("JWT jwt-token");
            respond(exchange, 200, "[{\"name\":\"Point A\"},{\"name\":\"Point B\"}]");
        });
        server.start();

        JtExpressClient client = new JtExpressClient(properties, mapper, HttpClient.newHttpClient());
        CreateShipmentRequest request = new CreateShipmentRequest(
                "ORDER-1",
                null,
                null,
                null,
                new ContactDetails("Jane", "+6591234567", null, "1 Marina Boulevard", "018956", null),
                List.of(new ItemDetails(null, null, null, BigDecimal.valueOf(500), "G", "Medical item", 1)));

        CreateShipmentResponse first = client.createShipment(request);
        CreateShipmentResponse second = client.createShipment(request);
        var connectivity = client.verifyConnectivity("PUDO");

        assertThat(first.trackingId()).isEqualTo("JT123");
        assertThat(second.trackingId()).isEqualTo("JT123");
        assertThat(loginCalls).hasValue(1);
        assertThat(shipmentCalls).hasValue(2);
        assertThat(connectivity.connected()).isTrue();
        assertThat(connectivity.distributionPointCount()).isEqualTo(2);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
