package com.oms.payment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.payment.adapter.PaymentCrypto;
import com.oms.payment.dto.PaymentDtos.CallbackRequest;
import com.oms.payment.service.PaymentService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信支付 v3 回调入口：验签、解密 resource、复用统一支付回调处理。
 */
@RestController
@RequestMapping("/api/v1/payment-callbacks/wechat")
public class WechatNotifyController {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int REPLAY_WINDOW_SECONDS = 300;

    private final PaymentService paymentService;

    @Value("${oms.payment.wechat.platform-public-key:}")
    private String platformPublicKey;

    @Value("${oms.payment.wechat.platform-serial-no:}")
    private String platformSerialNo;

    @Value("${oms.payment.wechat.api-v3-key:}")
    private String apiV3Key;

    public WechatNotifyController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/notify")
    public java.util.Map<String, String> notify(
            @RequestBody String payload,
            @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
            @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
            @RequestHeader(value = "Wechatpay-Serial", required = false) String serial) {
        if (timestamp == null || nonce == null || signature == null || serial == null) {
            return java.util.Map.of("code", "FAIL", "message", "缺少回调签名头");
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            return java.util.Map.of("code", "FAIL", "message", "时间戳非法");
        }
        if (Math.abs(Instant.now().getEpochSecond() - ts) > REPLAY_WINDOW_SECONDS) {
            return java.util.Map.of("code", "FAIL", "message", "回调已过期");
        }
        if (!platformSerialNo.equals(serial)) {
            return java.util.Map.of("code", "FAIL", "message", "证书序列号不匹配");
        }
        String signed = timestamp + "\n" + nonce + "\n" + payload + "\n";
        if (!PaymentCrypto.rsa256Verify(signed, signature, platformPublicKey)) {
            return java.util.Map.of("code", "FAIL", "message", "签名校验失败");
        }

        try {
            JsonNode root = MAPPER.readTree(payload);
            if (!"TRANSACTION.SUCCESS".equals(root.path("event_type").asText())) {
                return java.util.Map.of("code", "SUCCESS");
            }
            JsonNode resource = root.path("resource");
            String ciphertext = resource.path("ciphertext").asText();
            String nonceText = resource.path("nonce").asText();
            String associatedData = resource.path("associated_data").asText();
            String decrypted = decrypt(apiV3Key, nonceText, associatedData, ciphertext);
            JsonNode data = MAPPER.readTree(decrypted);
            String paymentNo = data.path("out_trade_no").asText(null);
            String channelTxnNo = data.path("transaction_id").asText(null);
            String tradeState = data.path("trade_state").asText(null);
            long amountMinor = data.path("amount").path("total").asLong(0);
            String currency = data.path("amount").path("currency").asText("SGD");
            if (paymentNo == null || channelTxnNo == null || !"SUCCESS".equals(tradeState)) {
                return java.util.Map.of("code", "SUCCESS");
            }
            BigDecimal amount = BigDecimal.valueOf(amountMinor, 2);
            paymentService.handleCallback(
                    "wechat",
                    new CallbackRequest(paymentNo, channelTxnNo, amount, "SUCCESS"));
            return java.util.Map.of("code", "SUCCESS", "message", "成功");
        } catch (Exception ex) {
            return java.util.Map.of("code", "FAIL", "message", ex.getMessage());
        }
    }

    private static String decrypt(String key, String nonce, String associatedData, String ciphertext)
            throws Exception {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] nonceBytes = nonce.getBytes(StandardCharsets.UTF_8);
        byte[] cipherBytes = Base64.getDecoder().decode(ciphertext);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(keyBytes, "AES"),
                new GCMParameterSpec(128, nonceBytes));
        if (associatedData != null && !associatedData.isEmpty()) {
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
        }
        return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
    }
}
