package com.oms.order.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 商城事件回调：订单状态变化后通知 BUZUD 商城。
 */
@Service
public class MallCallbackNotifier {

    private static final Logger log = LoggerFactory.getLogger(MallCallbackNotifier.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${oms.callback.base-url:http://localhost:8090}")
    private String baseUrl;

    @Value("${oms.callback.secret:demo-mall-callback-secret-change-me}")
    private String callbackSecret;

    public void notifyOrderStatus(String orderNo, String externalOrderNo, int status, String eventType) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventType", eventType);
        body.put("orderNo", orderNo);
        body.put("externalOrderNo", externalOrderNo);
        body.put("status", status);
        try {
            restTemplate.postForEntity(
                    baseUrl + "/api/v1/callback/oms",
                    signedRequest(body),
                    Void.class);
        } catch (Exception ex) {
            log.warn(
                    "商城回调发送失败 eventType={} orderNo={} error={}",
                    eventType,
                    orderNo,
                    ex.getMessage());
        }
    }

  public void notifyShipment(
      String orderNo,
      String externalOrderNo,
      String trackingNo,
      String carrier) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("eventType", "order.shipped");
    body.put("orderNo", orderNo);
    body.put("externalOrderNo", externalOrderNo);
    body.put("status", 4);
    body.put("carrier", carrier);
    body.put("trackingNo", trackingNo);
    body.put("logisticsStatus", "SHIPPED");
    try {
      restTemplate.postForEntity(
          baseUrl + "/api/v1/callback/oms",
          signedRequest(body),
          Void.class);
    } catch (Exception ex) {
      log.warn(
          "商城发货回调发送失败 orderNo={} error={}",
          orderNo,
          ex.getMessage());
    }
  }

  private HttpEntity<String> signedRequest(Map<String, Object> body) {
    try {
      String payload = MAPPER.writeValueAsString(body);
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("X-Oms-Signature", hmacSha256(callbackSecret, payload));
      return new HttpEntity<>(payload, headers);
    } catch (Exception ex) {
      throw new IllegalStateException("回调签名失败", ex);
    }
  }

  private static String hmacSha256(String secret, String data) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder(hash.length * 2);
    for (byte b : hash) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
