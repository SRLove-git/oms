package com.oms.payment.psp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.payment.dto.PaymentDtos.CreatePaymentResponse;
import com.oms.payment.dto.PaymentDtos.RefundRequest;
import com.oms.payment.entity.PaymentTransaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Stripe 国际卡 PSP 参考实现：创建 Checkout Session（托管结算页）、验签 webhook、退款。
 *
 * <p>说明：真实商户号/密钥就绪后，将 {@code oms.payment.card.secret-key} 与
 * {@code oms.payment.card.webhook-secret} 配置为生产值即可；若最终 PSP 不是 Stripe，
 * 仅需替换本类内部的 HTTP 调用，适配器与回调控制器契约保持不变。</p>
 */
@Service
public class StripeCardPspClient implements CardPspClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int WEBHOOK_REPLAY_WINDOW_SECONDS = 300;

    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of(
            "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA", "PYG",
            "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF");
    private static final Set<String> THREE_DECIMAL_CURRENCIES = Set.of("BHD", "JOD", "KWD", "OMR", "TND");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${oms.payment.card.secret-key:}")
    private String secretKey;

    @Value("${oms.payment.card.webhook-secret:}")
    private String webhookSecret;

    @Value("${oms.payment.card.api-base-url:https://api.stripe.com}")
    private String apiBaseUrl;

    @Value("${oms.payment.card.return-url-base:http://localhost:5175}")
    private String returnUrlBase;

    @Value("${oms.payment.card.success-path:/pay/result?status=success}")
    private String successPath;

    @Value("${oms.payment.card.cancel-path:/pay/result?status=cancel}")
    private String cancelPath;

    @Override
    public boolean isConfigured() {
        return hasText(secretKey) && hasText(webhookSecret);
    }

    @Override
    public CreatePaymentResponse createCheckoutSession(PaymentTransaction transaction) {
        assertConfigured();

        Map<String, String> params = new HashMap<>();
        params.put("mode", "payment");
        params.put("client_reference_id", transaction.getPaymentNo());
        params.put(
                "success_url",
                returnUrlBase + successPath + "&paymentNo=" + encode(transaction.getPaymentNo())
                        + "&orderNo=" + encode(transaction.getOrderNo()));
        params.put(
                "cancel_url",
                returnUrlBase + cancelPath + "&paymentNo=" + encode(transaction.getPaymentNo())
                        + "&orderNo=" + encode(transaction.getOrderNo()));
        params.put("payment_method_types[0]", "card");
        params.put("line_items[0][quantity]", "1");
        params.put(
                "line_items[0][price_data][currency]",
                transaction.getCurrency() == null ? "" : transaction.getCurrency().toLowerCase());
        params.put(
                "line_items[0][price_data][unit_amount]",
                String.valueOf(toMinorUnit(transaction.getAmount(), transaction.getCurrency())));
        params.put("line_items[0][price_data][product_data][name]", "Order " + transaction.getOrderNo());

        JsonNode body = post("/v1/checkout/sessions", params, transaction.getIdempotencyKey());
        String sessionUrl = requiredText(body, "url");
        return new CreatePaymentResponse(
                transaction.getPaymentNo(), transaction.getChannel(), sessionUrl, transaction.getAmount());
    }

    @Override
    public CardWebhookEvent parseWebhook(String payload, String signatureHeader) {
        assertConfigured();
        verifySignature(payload, signatureHeader);

        try {
            JsonNode root = MAPPER.readTree(payload);
            String type = root.path("type").asText();
            if (!"checkout.session.completed".equals(type)) {
                throw new BusinessException("不支持的 webhook 事件类型: " + type);
            }

            JsonNode session = root.path("data").path("object");
            String paymentNo = session.path("client_reference_id").asText(null);
            String paymentIntent = session.path("payment_intent").asText(null);
            String currency = session.path("currency").asText(null);
            long amountTotal = session.path("amount_total").asLong(0);
            if (!hasText(paymentNo) || !hasText(paymentIntent) || !hasText(currency)) {
                throw new BusinessException("webhook 事件缺少必要字段");
            }
            return new CardWebhookEvent(paymentNo, paymentIntent, fromMinorUnit(amountTotal, currency), currency);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("解析支付 webhook 失败: " + ex.getMessage());
        }
    }

    @Override
    public String refund(PaymentTransaction transaction, RefundRequest request) {
        assertConfigured();
        String paymentIntent = transaction.getChannelTxnNo();
        if (!hasText(paymentIntent)) {
            throw new BusinessException("缺少渠道交易号，无法发起退款");
        }

        Map<String, String> params = new HashMap<>();
        params.put("payment_intent", paymentIntent);
        if (request.amount() != null) {
            params.put("amount", String.valueOf(toMinorUnit(request.amount(), transaction.getCurrency())));
        }
        JsonNode body = post("/v1/refunds", params, null);
        return requiredText(body, "id");
    }

    static BigDecimal fromMinorUnit(long amountMinor, String currency) {
        return BigDecimal.valueOf(amountMinor).movePointLeft(minorUnitScale(currency));
    }

    static long toMinorUnit(BigDecimal amount, String currency) {
        return amount.movePointRight(minorUnitScale(currency))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    static int minorUnitScale(String currency) {
        String normalized = normalizeCurrency(currency);
        if (ZERO_DECIMAL_CURRENCIES.contains(normalized)) {
            return 0;
        }
        if (THREE_DECIMAL_CURRENCIES.contains(normalized)) {
            return 3;
        }
        return 2;
    }

    private void assertConfigured() {
        if (!isConfigured()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE.getCode(), "国际卡支付未配置 PSP 密钥");
        }
    }

    private JsonNode post(String path, Map<String, String> params, String idempotencyKey) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + path))
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(20));
            if (hasText(idempotencyKey)) {
                builder.header("Idempotency-Key", idempotencyKey);
            }
            builder.POST(HttpRequest.BodyPublishers.ofString(encodeForm(params), StandardCharsets.UTF_8));

            HttpResponse<String> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(
                        "国际卡 PSP 请求失败 status=" + response.statusCode() + " body=" + truncate(response.body()));
            }
            return MAPPER.readTree(response.body());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("调用国际卡 PSP 失败: " + ex.getMessage());
        }
    }

    private void verifySignature(String payload, String signatureHeader) {
        if (!hasText(signatureHeader)) {
            throw new BusinessException("缺少 Stripe-Signature 请求头");
        }

        String[] parts = signatureHeader.split(",");
        if (parts.length < 2 || !parts[0].startsWith("t=")) {
            throw new BusinessException("Stripe-Signature 格式非法");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(parts[0].substring(2));
        } catch (NumberFormatException ex) {
            throw new BusinessException("Stripe-Signature 时间戳非法");
        }

        List<String> signatures = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].startsWith("v1=")) {
                signatures.add(parts[i].substring(3));
            }
        }
        if (signatures.isEmpty()) {
            throw new BusinessException("Stripe-Signature 缺少 v1 签名");
        }

        if (Math.abs(Instant.now().getEpochSecond() - timestamp) > WEBHOOK_REPLAY_WINDOW_SECONDS) {
            throw new BusinessException("webhook 时间戳过期，可能存在重放风险");
        }

        String signedPayload = timestamp + "." + payload;
        byte[] expected = hmacSha256(webhookSecret, signedPayload);
        for (String signature : signatures) {
            byte[] actual;
            try {
                actual = hexDecode(signature);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (MessageDigest.isEqual(expected, actual)) {
                return;
            }
        }
        throw new BusinessException("webhook 签名校验失败");
    }

    private String encodeForm(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(entry.getKey()).append('=').append(encode(entry.getValue()));
        }
        return sb.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (!hasText(value)) {
            throw new BusinessException("国际卡 PSP 响应缺少字段 " + field);
        }
        return value;
    }

    private byte[] hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new BusinessException("webhook 签名计算失败");
        }
    }

    private byte[] hexDecode(String hex) {
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("hex 长度非法");
        }
        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < data.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("非法 hex 字符");
            }
            data[i] = (byte) ((hi << 4) + lo);
        }
        return data;
    }

    private static String normalizeCurrency(String currency) {
        return currency == null ? "" : currency.toUpperCase();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
