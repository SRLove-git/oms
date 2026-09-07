package com.oms.payment.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.payment.dto.PaymentDtos.CallbackRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.oms.payment.dto.PaymentDtos.CreatePaymentResponse;
import com.oms.payment.entity.PaymentTransaction;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 支付宝电脑网站支付（page pay）适配器。配置应用 ID 与商户私钥后生成收银台地址。
 */
@Component
public class AlipayPaymentAdapter implements PaymentAdapter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter ALIPAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${oms.payment.alipay.mock-only:true}")
    private boolean mockOnly;

    @Value("${oms.payment.alipay.app-id:}")
    private String appId;

    @Value("${oms.payment.alipay.private-key:}")
    private String privateKey;

    @Value("${oms.payment.alipay.gateway-url:https://openapi.alipay.com/gateway.do}")
    private String gatewayUrl;

    @Value("${oms.payment.alipay.notify-url:}")
    private String notifyUrl;

    @Value("${oms.payment.alipay.return-url:}")
    private String returnUrl;

    @Override
    public String channel() {
        return "alipay";
    }

    @Override
    public CreatePaymentResponse createPayment(
            CreatePaymentRequest request, PaymentTransaction transaction) {
        if (mockOnly || !configured()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE.getCode(), "支付宝渠道未配置");
        }
        try {
            String bizContent = MAPPER.writeValueAsString(Map.of(
                    "out_trade_no", transaction.getPaymentNo(),
                    "total_amount", transaction.getAmount().toPlainString(),
                    "subject", "BUZUD Order " + transaction.getOrderNo(),
                    "product_code", "FAST_INSTANT_TRADE_PAY"));
            Map<String, String> params = new LinkedHashMap<>();
            params.put("app_id", appId);
            params.put("method", "alipay.trade.page.pay");
            params.put("charset", "utf-8");
            params.put("sign_type", "RSA2");
            params.put("timestamp", LocalDateTime.now().format(ALIPAY_TIME));
            params.put("version", "1.0");
            params.put("notify_url", notifyUrl);
            params.put("return_url", returnUrl);
            params.put("biz_content", bizContent);

            String signContent = params.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .reduce((left, right) -> left + "&" + right)
                    .orElse("");
            String sign = PaymentCrypto.rsa256Sign(signContent, privateKey);
            params.put("sign", sign);

            String query = params.entrySet().stream()
                    .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                    .reduce((left, right) -> left + "&" + right)
                    .orElse("");
            return new CreatePaymentResponse(
                    transaction.getPaymentNo(), channel(), gatewayUrl + "?" + query, transaction.getAmount());
        } catch (Exception ex) {
            throw new BusinessException("创建支付宝支付失败: " + ex.getMessage());
        }
    }

    @Override
    public void verifyCallback(CallbackRequest request, PaymentTransaction transaction) {
        if (mockOnly) {
            return;
        }
        if (request.amount() == null || transaction.getAmount().compareTo(request.amount()) != 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "支付宝回调金额与支付单不一致");
        }
        if (!"SUCCESS".equalsIgnoreCase(request.status())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "支付宝支付状态非成功");
        }
    }

    private boolean configured() {
        return hasText(appId) && hasText(privateKey) && hasText(notifyUrl);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
