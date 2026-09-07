package com.oms.integration.provider;

import com.oms.integration.entity.NotificationMessage;
import org.springframework.stereotype.Component;

@Component
public class MockNotificationProvider implements NotificationProvider {

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
