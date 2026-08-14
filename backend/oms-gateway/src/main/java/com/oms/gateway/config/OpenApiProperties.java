package com.oms.gateway.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 商城开放 API 客户端配置。
 *
 * <pre>
 * oms:
 *   open-api:
 *     enabled: true
 *     timestamp-window-seconds: 300
 *     clients:
 *       demo-mall:
 *         secret: xxx
 *         merchant-id: 1
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "oms.open-api")
public class OpenApiProperties {

    private boolean enabled = true;
    private int timestampWindowSeconds = 300;
    private Map<String, Client> clients = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTimestampWindowSeconds() {
        return timestampWindowSeconds;
    }

    public void setTimestampWindowSeconds(int timestampWindowSeconds) {
        this.timestampWindowSeconds = timestampWindowSeconds;
    }

    public Map<String, Client> getClients() {
        return clients;
    }

    public void setClients(Map<String, Client> clients) {
        this.clients = clients;
    }

    public static class Client {

        private String secret;
        private Long merchantId;
        private boolean enabled = true;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public Long getMerchantId() {
            return merchantId;
        }

        public void setMerchantId(Long merchantId) {
            this.merchantId = merchantId;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
