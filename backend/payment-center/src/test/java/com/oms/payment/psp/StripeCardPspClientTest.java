package com.oms.payment.psp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oms.common.core.exception.BusinessException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StripeCardPspClientTest {

    private static final String WEBHOOK_SECRET = "whsec_test_123";

    private StripeCardPspClient client;

    @BeforeEach
    void setUp() throws Exception {
        client = new StripeCardPspClient();
        setField(client, "secretKey", "sk_test_123");
        setField(client, "webhookSecret", WEBHOOK_SECRET);
    }

    @Test
    void shouldConvertMinorUnitsForCommonCurrencies() {
        assertThat(StripeCardPspClient.toMinorUnit(new BigDecimal("100.00"), "SGD")).isEqualTo(10000L);
        assertThat(StripeCardPspClient.fromMinorUnit(10000L, "SGD")).isEqualByComparingTo("100.00");

        assertThat(StripeCardPspClient.toMinorUnit(new BigDecimal("100"), "JPY")).isEqualTo(100L);
        assertThat(StripeCardPspClient.fromMinorUnit(100L, "JPY")).isEqualByComparingTo("100");

        assertThat(StripeCardPspClient.toMinorUnit(new BigDecimal("100.123"), "KWD")).isEqualTo(100123L);
        assertThat(StripeCardPspClient.fromMinorUnit(100123L, "KWD")).isEqualByComparingTo("100.123");
    }

    @Test
    void shouldParseValidSignedWebhook() {
        String payload = "{\"type\":\"checkout.session.completed\",\"data\":{\"object\":{"
                + "\"client_reference_id\":\"P001\",\"payment_intent\":\"pi_1\","
                + "\"currency\":\"sgd\",\"amount_total\":10000}}}";
        String signatureHeader = signedHeader(payload);

        CardWebhookEvent event = client.parseWebhook(payload, signatureHeader);

        assertThat(event.paymentNo()).isEqualTo("P001");
        assertThat(event.channelTxnNo()).isEqualTo("pi_1");
        assertThat(event.amount()).isEqualByComparingTo("100.00");
        assertThat(event.currency()).isEqualTo("sgd");
    }

    @Test
    void shouldRejectTamperedWebhook() {
        String payload = "{\"type\":\"checkout.session.completed\",\"data\":{\"object\":{"
                + "\"client_reference_id\":\"P001\",\"payment_intent\":\"pi_1\","
                + "\"currency\":\"sgd\",\"amount_total\":10000}}}";
        String tampered = payload.replace("10000", "1");
        String signatureHeader = signedHeader(payload);

        assertThatThrownBy(() -> client.parseWebhook(tampered, signatureHeader))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("签名校验失败");
    }

    @Test
    void shouldRejectExpiredWebhook() {
        String payload = "{\"type\":\"checkout.session.completed\",\"data\":{\"object\":{"
                + "\"client_reference_id\":\"P001\",\"payment_intent\":\"pi_1\","
                + "\"currency\":\"sgd\",\"amount_total\":10000}}}";
        long oldTimestamp = Instant.now().getEpochSecond() - 600;
        String signatureHeader = "t=" + oldTimestamp + ",v1=" + signatureFor(oldTimestamp, payload);

        assertThatThrownBy(() -> client.parseWebhook(payload, signatureHeader))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("时间戳过期");
    }

    @Test
    void shouldRejectMissingSignatureHeader() {
        String payload = "{\"type\":\"checkout.session.completed\"}";

        assertThatThrownBy(() -> client.parseWebhook(payload, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少 Stripe-Signature");
    }

    private String signedHeader(String payload) {
        long timestamp = Instant.now().getEpochSecond();
        return "t=" + timestamp + ",v1=" + signatureFor(timestamp, payload);
    }

    private String signatureFor(long timestamp, String payload) {
        String signedPayload = timestamp + "." + payload;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            return hex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
