package com.oms.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.oms.gateway.config.OpenApiProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class OpenApiAuthFilterTest {

    private static final String APP_ID = "test-mall";
    private static final String SECRET = "test-secret";

    private OpenApiAuthFilter filter;
    private AtomicReference<ServerWebExchange> forwarded;

    @BeforeEach
    void setUp() {
        OpenApiProperties.Client client = new OpenApiProperties.Client();
        client.setSecret(SECRET);
        client.setMerchantId(1L);
        client.setEnabled(true);
        OpenApiProperties properties = new OpenApiProperties();
        properties.setEnabled(true);
        properties.setTimestampWindowSeconds(300);
        Map<String, OpenApiProperties.Client> clients = new HashMap<>();
        clients.put(APP_ID, client);
        properties.setClients(clients);
        filter = new OpenApiAuthFilter(properties);
        forwarded = new AtomicReference<>();
    }

    private GatewayFilterChain recordingChain() {
        return exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
    }

    private HttpHeaders signedHeaders(String method, String path, String body) throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = "nonce-" + System.nanoTime();
        String stringToSign = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + sha256Hex(body);
        String sign = hmacSha256Hex(SECRET, stringToSign);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-App-Id", APP_ID);
        headers.add("X-Timestamp", timestamp);
        headers.add("X-Nonce", nonce);
        headers.add("X-Sign", sign);
        return headers;
    }

    @Test
    void shouldForwardWhenSignatureValid() throws Exception {
        String body = "{\"externalOrderNo\":\"M1\",\"items\":[{\"skuId\":1,\"quantity\":1}]}";
        HttpHeaders headers = signedHeaders("POST", "/api/v1/open/orders", body);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/open/orders").headers(headers).body(body));

        filter.filter(exchange, recordingChain()).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Merchant-Id")).isEqualTo("1");
    }

    @Test
    void shouldForwardWithoutSignatureForNonOpenPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").build());

        filter.filter(exchange, recordingChain()).block();

        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    void shouldRejectWhenSignInvalid() throws Exception {
        String body = "{\"externalOrderNo\":\"M1\"}";
        HttpHeaders headers = signedHeaders("POST", "/api/v1/open/orders", body);
        headers.set("X-Sign", "deadbeef");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/open/orders").headers(headers).body(body));

        filter.filter(exchange, recordingChain()).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectWhenHeadersMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/open/orders").build());

        filter.filter(exchange, recordingChain()).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectWhenTimestampExpired() throws Exception {
        String body = "{}";
        HttpHeaders headers = signedHeaders("POST", "/api/v1/open/orders", body);
        headers.set("X-Timestamp", String.valueOf(System.currentTimeMillis() / 1000 - 3600));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/open/orders").headers(headers).body(body));

        filter.filter(exchange, recordingChain()).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectWhenNonceReplayed() throws Exception {
        String body = "{}";
        HttpHeaders headers = signedHeaders("POST", "/api/v1/open/orders", body);
        MockServerWebExchange first = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/open/orders").headers(headers).body(body));
        filter.filter(first, recordingChain()).block();
        assertThat(forwarded.get()).isNotNull();

        forwarded.set(null);
        MockServerWebExchange replay = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/open/orders").headers(headers).body(body));
        filter.filter(replay, recordingChain()).block();

        assertThat(forwarded.get()).isNull();
        assertThat(replay.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String sha256Hex(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return toHex(hash);
    }

    private String hmacSha256Hex(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return toHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
