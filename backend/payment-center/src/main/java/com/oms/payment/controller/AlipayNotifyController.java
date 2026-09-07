package com.oms.payment.controller;

import com.oms.payment.adapter.PaymentCrypto;
import com.oms.payment.dto.PaymentDtos.CallbackRequest;
import com.oms.payment.service.PaymentService;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝异步通知入口：验签 RSA2 后复用统一支付回调处理。
 */
@RestController
@RequestMapping("/api/v1/payment-callbacks/alipay")
public class AlipayNotifyController {

    private final PaymentService paymentService;

    @Value("${oms.payment.alipay.app-id:}")
    private String appId;

    @Value("${oms.payment.alipay.alipay-public-key:}")
    private String alipayPublicKey;

    public AlipayNotifyController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping(value = "/notify", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String notify(@RequestBody String rawBody) {
        Map<String, String> params = parseForm(rawBody);
        String sign = params.remove("sign");
        String signType = params.remove("sign_type");
        if (sign == null || !"RSA2".equals(signType)) {
            return "failure";
        }
        if (!appId.equals(params.get("app_id"))) {
            return "failure";
        }
        String content = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        if (!PaymentCrypto.rsa256Verify(content, sign, alipayPublicKey)) {
            return "failure";
        }

        String status = params.get("trade_status");
        if (!"TRADE_SUCCESS".equals(status) && !"TRADE_FINISHED".equals(status)) {
            return "success";
        }
        String paymentNo = params.get("out_trade_no");
        String channelTxnNo = params.get("trade_no");
        String totalAmount = params.get("total_amount");
        if (paymentNo == null || channelTxnNo == null || totalAmount == null) {
            return "failure";
        }
        paymentService.handleCallback(
                "alipay",
                new CallbackRequest(paymentNo, channelTxnNo, new BigDecimal(totalAmount), "SUCCESS"));
        return "success";
    }

    private static Map<String, String> parseForm(String raw) {
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            int idx = pair.indexOf('=');
            if (idx < 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }
}
