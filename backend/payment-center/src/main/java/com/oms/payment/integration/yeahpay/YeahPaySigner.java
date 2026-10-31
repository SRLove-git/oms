package com.oms.payment.integration.yeahpay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** YeahPay V1.0 SHA-512 签名：URL、请求头与原始 JSON 必须逐字参与签名。 */
final class YeahPaySigner {

    static final String VERSION = "1.0";
    static final String ALGORITHM = "SHA-512";
    private static final String NONCE_CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private YeahPaySigner() {
    }

    static String sign(
            String url,
            String appId,
            String timestamp,
            String nonce,
            String rawBody,
            String apiKey) {
        String value = url + "\n" + appId + "\n" + timestamp + "\n" + VERSION + "\n"
                + nonce + "\n" + rawBody + "\n" + apiKey;
        try {
            byte[] digest = MessageDigest.getInstance(ALGORITHM)
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("YeahPay SHA-512 签名失败", ex);
        }
    }

    static String nonce() {
        StringBuilder value = new StringBuilder(16);
        for (int index = 0; index < 16; index++) {
            value.append(NONCE_CHARS.charAt(RANDOM.nextInt(NONCE_CHARS.length())));
        }
        return value.toString();
    }
}
