package com.oms.common.core.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 签名工具，供商城回调与开放 API 客户端复用。
 *
 * <p>签名串与网关 OpenApiAuthFilter / 商城 OmsSigner 保持一致：
 * {@code METHOD\nPATH\nTIMESTAMP\nNONCE\nSHA256_HEX(BODY)}，签名为小写 hex。
 */
public final class HmacSigner {

    private static final SecureRandom RANDOM = new SecureRandom();

    private HmacSigner() {}

    public static String sign(
            String secret, String method, String path, String timestamp, String nonce, String bodySha256Hex) {
        String stringToSign = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + bodySha256Hex;
        return hmacSha256Hex(secret, stringToSign);
    }

    public static String sha256Hex(String body) {
        return hex(sha256(body == null ? "" : body));
    }

    public static String hmacSha256Hex(String secret, String value) {
        return hex(hmacSha256(secret, value));
    }

    public static String nonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return hex(bytes);
    }

    public static String timestamp() {
        return String.valueOf(System.currentTimeMillis() / 1000);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private static byte[] hmacSha256(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("HmacSHA256 签名失败", ex);
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
