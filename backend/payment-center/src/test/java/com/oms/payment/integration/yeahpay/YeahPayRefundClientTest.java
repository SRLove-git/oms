package com.oms.payment.integration.yeahpay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

class YeahPayRefundClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private YeahPayRefundClient client;
    private volatile String queryResponse;
    private volatile String refundResponse;
    private volatile String receivedBody;
    private volatile String receivedTimestamp;
    private volatile String receivedNonce;
    private volatile String receivedSignature;
    private final AtomicInteger refundCalls = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        queryResponse = "{\"code\":10012,\"data\":null}";
        refundResponse = "{\"code\":0,\"data\":{\"status\":3,"
                + "\"leshuaRefundId\":\"YR-1001\"}}";
        server.createContext("/acceptance-open-api/order/queryRefund",
                exchange -> respond(exchange, queryResponse));
        server.createContext("/acceptance-open-api/unified/reversalOperation", exchange -> {
            refundCalls.incrementAndGet();
            respond(exchange, refundResponse);
        });
        server.createContext("/acceptance-open-api/cardOrder/getTradeType",
                exchange -> respond(exchange, "{\"code\":0,\"data\":[]}"));
        server.start();

        String origin = "http://localhost:" + server.getAddress().getPort();
        client = new YeahPayRefundClient(mapper);
        ReflectionTestUtils.setField(client, "enabled", true);
        ReflectionTestUtils.setField(client, "appId", "app-test");
        ReflectionTestUtils.setField(client, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(client, "merchantId", "merchant-test");
        ReflectionTestUtils.setField(client, "apiBaseUrl", origin + "/acceptance-open-api");
        ReflectionTestUtils.setField(client, "signatureDomain", origin);
        ReflectionTestUtils.setField(client, "connectTimeoutMs", 1000);
        ReflectionTestUtils.setField(client, "readTimeoutMs", 2000);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldSubmitIdempotentFullRefundAndPreservePendingStatus() throws Exception {
        YeahPayRefundClient.RefundResult result = client.refund(
                "ORDER2026103100001", "YP-1001", "RF2026103112345600001");

        assertEquals(3, result.status());
        assertEquals("YR-1001", result.providerRefundNo());
        assertEquals(1, refundCalls.get());
        JsonNode body = mapper.readTree(receivedBody);
        assertEquals(1, body.path("sceneType").asInt());
        assertEquals("merchant-test", body.path("merchantId").asText());
        assertEquals("YP-1001", body.path("leshuaOrderId").asText());
        assertEquals("RF2026103112345600001", body.path("merchantRefundId").asText());
        assertFalse(body.has("amount"), "全额退款按文档不传 amount");
        assertEquals(
                YeahPaySigner.sign(
                        "/acceptance-open-api/unified/reversalOperation",
                        "app-test",
                        receivedTimestamp,
                        receivedNonce,
                        receivedBody,
                        "test-api-key"),
                receivedSignature);
    }

    @Test
    void shouldQueryExistingRefundBeforeSubmittingAnotherRequest() {
        queryResponse = "{\"code\":0,\"data\":{\"status\":11,"
                + "\"leshuaRefundId\":\"YR-DONE\"}}";

        YeahPayRefundClient.RefundResult result = client.refund(
                "ORDER2026103100001", "YP-1001", "RF2026103112345600001");

        assertEquals(2, result.status());
        assertEquals("YR-DONE", result.providerRefundNo());
        assertEquals(0, refundCalls.get());
    }

    @Test
    void shouldVerifyMerchantConfigurationWithoutCreatingATransaction() {
        var result = client.verifyConfiguration();

        assertEquals(true, result.get("configured"));
        assertEquals(true, result.get("connected"));
        assertNotNull(receivedSignature);
        assertEquals(0, refundCalls.get());
    }

    @Test
    void shouldBeConstructibleBySpring() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ObjectMapper.class);
            context.register(YeahPayRefundClient.class);
            context.refresh();

            assertNotNull(context.getBean(YeahPayRefundClient.class));
        }
    }

    private void respond(HttpExchange exchange, String responseBody) throws IOException {
        receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        receivedTimestamp = exchange.getRequestHeaders().getFirst("timestamp");
        receivedNonce = exchange.getRequestHeaders().getFirst("nonce");
        receivedSignature = exchange.getRequestHeaders().getFirst("signature");
        byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }
}
