package com.oms.integration.jtexpress;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "oms.integration.jtexpress")
public class JtExpressProperties {

    private boolean enabled;
    private String baseUrl = "https://demoedishops.jtexpress.sg/jts-service-doorstep";
    private String email;
    private String password;
    private String merchantCode;
    private String serviceCode = "DOM123";
    private String webhookSecret;

    public boolean isConfigured() {
        return enabled && hasText(baseUrl) && hasText(email) && hasText(password);
    }

    /**
     * webhook 签名密钥是否已配置。建单只依赖账号密码，但轨迹回写还依赖该密钥。
     */
    public boolean isWebhookConfigured() {
        return hasText(webhookSecret);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = trimTrailingSlash(baseUrl);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getMerchantCode() {
        return merchantCode;
    }

    public void setMerchantCode(String merchantCode) {
        this.merchantCode = merchantCode;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public void setServiceCode(String serviceCode) {
        this.serviceCode = serviceCode;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
