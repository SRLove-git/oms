package com.oms.payment.integration.yeahpay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** YeahPay 退款与退款查询客户端（Trade API §4.6、§4.9）。 */
@Component
public class YeahPayRefundClient {

    private static final String QUERY_REFUND_PATH = "/order/queryRefund";
    private static final String REFUND_PATH = "/unified/reversalOperation";
    private static final String VERIFY_PATH = "/cardOrder/getTradeType";

    private final ObjectMapper mapper;
    private volatile HttpClient httpClient;

    @Value("${oms.payment.yeahpay.enabled:false}")
    private boolean enabled;

    @Value("${oms.payment.yeahpay.app-id:}")
    private String appId;

    @Value("${oms.payment.yeahpay.api-key:}")
    private String apiKey;

    @Value("${oms.payment.yeahpay.merchant-id:}")
    private String merchantId;

    @Value("${oms.payment.yeahpay.api-base-url:https://open-api.yeahpay.sg/acceptance/acceptance-open-api}")
    private String apiBaseUrl;

    @Value("${oms.payment.yeahpay.signature-domain:https://open-api.yeahpay.sg/acceptance}")
    private String signatureDomain;

    @Value("${oms.payment.yeahpay.connect-timeout-ms:10000}")
    private int connectTimeoutMs;

    @Value("${oms.payment.yeahpay.read-timeout-ms:20000}")
    private int readTimeoutMs;

    public YeahPayRefundClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public record RefundResult(Integer status, String providerRefundNo, String message) {
    }

    public RefundResult refund(
            String thirdOrderId, String leshuaOrderId, String merchantRefundId) {
        validateConfiguration();
        if (!hasText(thirdOrderId) || !hasText(leshuaOrderId) || !hasText(merchantRefundId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "YeahPay 退款参数不完整");
        }

        RefundResult existing = queryRefund(thirdOrderId, leshuaOrderId, merchantRefundId);
        if (existing != null) {
            return existing;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sceneType", 1);
        body.put("merchantId", merchantId);
        // 文档规定 amount 不传表示全额退款；当前生产先只开放全额退款。
        body.put("leshuaOrderId", leshuaOrderId);
        body.put("thirdOrderId", thirdOrderId);
        body.put("merchantRefundId", merchantRefundId);
        JsonNode response = post(REFUND_PATH, body);
        String code = responseCode(response);
        if (!isSuccess(code)) {
            return new RefundResult(1, null, "YeahPay 退款请求被拒绝 code=" + code);
        }
        JsonNode data = requireData(response, code);
        int status = requiredStatus(data, code);
        if (status != 1 && status != 2 && status != 3) {
            throw providerError("退款响应状态非法", code, response);
        }
        return new RefundResult(
                status,
                optionalText(data, "leshuaRefundId", code, response),
                status == 2 ? "退款成功" : status == 3 ? "退款处理中" : "退款失败");
    }

    public Map<String, Object> verifyConfiguration() {
        validateConfiguration();
        JsonNode response = post(VERIFY_PATH, Map.of("merchantId", merchantId));
        String code = responseCode(response);
        if (!isSuccess(code)) {
            throw providerError("商户配置校验失败", code, response);
        }
        return Map.of(
                "configured", true,
                "connected", true,
                "merchantId", merchantId,
                "refundMode", "full");
    }

    private RefundResult queryRefund(
            String thirdOrderId, String leshuaOrderId, String merchantRefundId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantId", merchantId);
        body.put("thirdOrderId", thirdOrderId);
        body.put("leshuaOrderId", leshuaOrderId);
        body.put("merchantRefundId", merchantRefundId);
        JsonNode response = post(QUERY_REFUND_PATH, body);
        String code = responseCode(response);
        if ("10012".equals(code)) {
            return null;
        }
        if (!isSuccess(code)) {
            throw providerError("退款查询失败", code, response);
        }
        JsonNode data = requireData(response, code);
        int providerStatus = requiredStatus(data, code);
        int normalized = switch (providerStatus) {
            case 11 -> 2;
            case 10, 0 -> 3;
            case 12, 1 -> 1;
            default -> throw providerError("退款查询状态非法", code, response);
        };
        return new RefundResult(
                normalized,
                optionalText(data, "leshuaRefundId", code, response),
                normalized == 2 ? "退款成功" : normalized == 3 ? "退款处理中" : "退款失败");
    }

    private JsonNode post(String path, Object body) {
        String rawBody;
        try {
            rawBody = mapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new IllegalStateException("YeahPay 请求序列化失败", ex);
        }
        String fullUrl = joinUrl(apiBaseUrl, path);
        if (!fullUrl.startsWith(signatureDomain)) {
            throw new IllegalStateException("YeahPay signature-domain 与 api-base-url 不匹配");
        }
        String signatureUrl = fullUrl.substring(signatureDomain.length());
        if (!signatureUrl.startsWith("/")) {
            signatureUrl = "/" + signatureUrl;
        }
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = YeahPaySigner.nonce();
        String signature = YeahPaySigner.sign(
                signatureUrl, appId, timestamp, nonce, rawBody, apiKey);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .timeout(Duration.ofMillis(readTimeoutMs))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("version", YeahPaySigner.VERSION)
                .header("timestamp", timestamp)
                .header("algorithm", YeahPaySigner.ALGORITHM)
                .header("signature", signature)
                .header("appId", appId)
                .header("nonce", nonce)
                .POST(HttpRequest.BodyPublishers.ofString(rawBody))
                .build();
        try {
            HttpResponse<String> response = httpClient().send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(
                        ErrorCode.SERVICE_UNAVAILABLE.getCode(),
                        "YeahPay 请求失败 status=" + response.statusCode());
            }
            return mapper.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(
                    ErrorCode.SERVICE_UNAVAILABLE.getCode(), "YeahPay 请求被中断");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                    ErrorCode.SERVICE_UNAVAILABLE.getCode(), "YeahPay 请求失败");
        }
    }

    private void validateConfiguration() {
        if (!enabled || !hasText(appId) || !hasText(apiKey) || !hasText(merchantId)
                || !hasText(apiBaseUrl) || !hasText(signatureDomain)
                || connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
            throw new BusinessException(
                    ErrorCode.SERVICE_UNAVAILABLE.getCode(), "YeahPay 退款配置不完整");
        }
    }

    private HttpClient httpClient() {
        HttpClient client = httpClient;
        if (client != null) {
            return client;
        }
        synchronized (this) {
            if (httpClient == null) {
                httpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                        .build();
            }
            return httpClient;
        }
    }

    private static String responseCode(JsonNode response) {
        JsonNode code = response == null ? null : response.get("code");
        if (code == null || !(code.isTextual() || code.isIntegralNumber())
                || !code.asText().matches("-?(0|[1-9][0-9]{0,9})")) {
            throw new BusinessException(
                    ErrorCode.SERVICE_UNAVAILABLE.getCode(), "YeahPay 响应业务码非法");
        }
        return code.asText();
    }

    private static JsonNode requireData(JsonNode response, String code) {
        JsonNode data = response.path("data");
        if (!data.isObject()) {
            throw providerError("响应缺少 data", code, response);
        }
        return data;
    }

    private static int requiredStatus(JsonNode data, String code) {
        JsonNode status = data.get("status");
        if (status == null || !(status.isIntegralNumber() || status.isTextual())
                || !status.asText().matches("0|[1-9][0-9]*")) {
            throw providerError("响应状态非法", code, null);
        }
        try {
            return Integer.parseInt(status.asText());
        } catch (NumberFormatException ex) {
            throw providerError("响应状态超出范围", code, null);
        }
    }

    private static String optionalText(
            JsonNode data, String field, String code, JsonNode response) {
        JsonNode value = data.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || !hasText(value.asText())) {
            throw providerError("响应字段 " + field + " 非法", code, response);
        }
        return value.asText();
    }

    private static BusinessException providerError(
            String reason, String code, JsonNode response) {
        String traceId = response == null ? null : response.path("traceId").asText(null);
        return new BusinessException(
                ErrorCode.SERVICE_UNAVAILABLE.getCode(),
                "YeahPay " + reason + " code=" + code
                        + (hasText(traceId) ? " traceId=" + traceId : ""));
    }

    private static boolean isSuccess(String code) {
        return "0".equals(code) || "200".equals(code);
    }

    private static String joinUrl(String base, String path) {
        return base.replaceAll("/+$", "") + "/" + path.replaceAll("^/+", "");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
