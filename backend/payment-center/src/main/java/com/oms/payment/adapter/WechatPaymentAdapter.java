package com.oms.payment.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.payment.dto.PaymentDtos.CallbackRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentResponse;
import com.oms.payment.entity.PaymentTransaction;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 微信支付 Native API v3 适配器。配置商户号、证书序列号与商户私钥后创建 Native 支付二维码。
 */
@Component
public class WechatPaymentAdapter implements PaymentAdapter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String NATIVE_PATH = "/v3/pay/transactions/native";
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${oms.payment.wechat.mock-only:true}")
    private boolean mockOnly;

    @Value("${oms.payment.wechat.mch-id:}")
    private String mchId;

    @Value("${oms.payment.wechat.app-id:}")
    private String appId;

    @Value("${oms.payment.wechat.serial-no:}")
    private String serialNo;

    @Value("${oms.payment.wechat.private-key:}")
    private String privateKey;

    @Value("${oms.payment.wechat.notify-url:}")
    private String notifyUrl;

    @Value("${oms.payment.wechat.gateway-url:https://api.mch.weixin.qq.com}")
    private String gatewayUrl;

    @Override
    public String channel() {
        return "wechat";
    }

    @Override
    public CreatePaymentResponse createPayment(
            CreatePaymentRequest request, PaymentTransaction transaction) {
        if (mockOnly || !configured()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE.getCode(), "微信支付渠道未配置");
        }
        try {
            String body = MAPPER.writeValueAsString(java.util.Map.of(
                    "appid", appId,
                    "mchid", mchId,
                    "description", "BUZUD Order " + transaction.getOrderNo(),
                    "out_trade_no", transaction.getPaymentNo(),
                    "notify_url", notifyUrl,
                    "amount", java.util.Map.of(
                            "total", toFen(transaction.getAmount()),
                            "currency", transaction.getCurrency())));
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String nonce = UUID.randomUUID().toString().replace("-", "");
            String message = "POST\n" + NATIVE_PATH + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n";
            String signature = PaymentCrypto.rsa256Sign(message, privateKey);
            String authorization = "WECHATPAY2-SHA256-RSA2048 mchid=\"" + mchId
                    + "\",nonce_str=\"" + nonce
                    + "\",timestamp=\"" + timestamp
                    + "\",serial_no=\"" + serialNo
                    + "\",signature=\"" + signature + "\"";

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(gatewayUrl + NATIVE_PATH))
                    .header("Authorization", authorization)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("微信支付下单失败 status=" + response.statusCode());
            }
            JsonNode root = MAPPER.readTree(response.body());
            String codeUrl = root.path("code_url").asText(null);
            if (codeUrl == null || codeUrl.isBlank()) {
                throw new BusinessException("微信支付未返回 code_url");
            }
            return new CreatePaymentResponse(
                    transaction.getPaymentNo(), channel(), codeUrl, transaction.getAmount());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("调用微信支付失败: " + ex.getMessage());
        }
    }

    @Override
    public void verifyCallback(CallbackRequest request, PaymentTransaction transaction) {
        if (mockOnly) {
            return;
        }
        if (request.amount() == null || transaction.getAmount().compareTo(request.amount()) != 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "微信回调金额与支付单不一致");
        }
        if (!"SUCCESS".equalsIgnoreCase(request.status())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "微信支付状态非成功");
        }
    }

    @Override
    public String refund(PaymentTransaction transaction,
            com.oms.payment.dto.PaymentDtos.RefundRequest request) {
        throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "微信退款需配置真实证书与退款 API");
    }

    private boolean configured() {
        return hasText(mchId) && hasText(appId) && hasText(serialNo) && hasText(privateKey);
    }

    private static int toFen(java.math.BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).intValueExact();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
