package com.oms.payment.adapter;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 微信/支付宝 RSA2 签名工具。私钥支持 PKCS#8 PEM，公钥支持 X.509 PEM。
 */
public final class PaymentCrypto {

    private PaymentCrypto() {
    }

    public static String rsa256Sign(String content, String privateKeyPem) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(parsePrivateKey(privateKeyPem));
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception ex) {
            throw new IllegalStateException("RSA2 签名失败: " + ex.getMessage(), ex);
        }
    }

    public static boolean rsa256Verify(String content, String sign, String publicKeyPem) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(parsePublicKey(publicKeyPem));
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(sign));
        } catch (Exception ex) {
            return false;
        }
    }

    private static PrivateKey parsePrivateKey(String pem) {
        try {
            String base64 = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64);
            try {
                return KeyFactory.getInstance("RSA")
                        .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            } catch (Exception ex) {
                // PKCS#1 fallback: prepend PKCS#8 RSA header.
                byte[] pkcs8 = new byte[keyBytes.length + 26];
                System.arraycopy(new byte[] {
                        0x30, (byte) 0x82, 0, 0,
                        0x02, 0x01, 0x00,
                        0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
                        (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01,
                        0x05, 0x00,
                        0x04, (byte) 0x82, 0, 0
                }, 0, pkcs8, 0, 22);
                pkcs8[2] = (byte) (keyBytes.length >> 8);
                pkcs8[3] = (byte) keyBytes.length;
                pkcs8[20] = (byte) (keyBytes.length >> 8);
                pkcs8[21] = (byte) keyBytes.length;
                System.arraycopy(keyBytes, 0, pkcs8, 26, keyBytes.length);
                return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("解析 RSA 私钥失败: " + ex.getMessage(), ex);
        }
    }

    private static PublicKey parsePublicKey(String pem) {
        try {
            String base64 = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (Exception ex) {
            throw new IllegalStateException("解析 RSA 公钥失败: " + ex.getMessage(), ex);
        }
    }
}
