package com.oms.integration.jtexpress;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class JtExpressWebhookVerifier {

    private final JtExpressProperties properties;

    public JtExpressWebhookVerifier(JtExpressProperties properties) {
        this.properties = properties;
    }

    public boolean verify(String requestId, String signature) {
        String secret = properties.getWebhookSecret();
        if (!hasText(secret) || !hasText(requestId) || !hasText(signature)) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            String expected = toHex(mac.doFinal(requestId.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    signature.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII));
        } catch (Exception ex) {
            throw new IllegalStateException("无法校验 J&T webhook 签名", ex);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
