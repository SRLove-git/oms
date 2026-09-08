package com.oms.integration.provider;

import com.oms.integration.entity.NotificationMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "oms.integration.notification.provider",
        havingValue = "mock",
        matchIfMissing = true)
public class MockNotificationProvider implements NotificationProvider {

    @Override
    public boolean supports(String channel) {
        return true;
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public void send(NotificationMessage message) {
        if (message.getReceiver() != null && message.getReceiver().startsWith("fail:")) {
            throw new IllegalStateException("模拟渠道发送失败");
        }
    }
}
