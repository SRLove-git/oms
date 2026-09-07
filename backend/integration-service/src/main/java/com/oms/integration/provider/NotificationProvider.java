package com.oms.integration.provider;

import com.oms.integration.entity.NotificationMessage;

/**
 * 通知渠道真实发送抽象：短信/邮件/微信模板/站内信统一走该接口，
 * 真实渠道配置就绪时替换 HTTP 实现，mock 模式保留本地发送。
 */
public interface NotificationProvider {

    boolean isConfigured();

    void send(NotificationMessage message);
}
