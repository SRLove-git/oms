package com.oms.integration.provider;

import com.oms.integration.entity.NotificationMessage;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * 阿里云 DirectMail 新加坡区域 SMTP 邮件发送适配器。
 */
@Component
@ConditionalOnProperty(
        name = "oms.integration.notification.provider",
        havingValue = "smtp")
public class SmtpNotificationProvider implements NotificationProvider {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

    @Value("${oms.integration.notification.reply-to:}")
    private String replyTo;

    public SmtpNotificationProvider(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public boolean supports(String channel) {
        return "email".equals(channel);
    }

    @Override
    public boolean isConfigured() {
        return hasText(from);
    }

    @Override
    public void send(NotificationMessage message) {
        if (!"email".equals(message.getChannel())) {
            throw new IllegalStateException("SMTP provider 仅支持 email 渠道");
        }
        if (message.getReceiver() == null || message.getReceiver().isBlank()) {
            throw new IllegalStateException("收件人不能为空");
        }

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
        try {
            helper.setFrom(from);
            helper.setTo(message.getReceiver());
            if (hasText(replyTo)) {
                helper.setReplyTo(replyTo);
            }
            helper.setSubject(message.getTitle() == null || message.getTitle().isBlank()
                    ? "Medical SG"
                    : message.getTitle());
            helper.setText(message.getContent() == null ? "" : message.getContent(), false);
            mailSender.send(mimeMessage);
        } catch (Exception ex) {
            throw new IllegalStateException("SMTP 邮件发送失败: " + ex.getMessage(), ex);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
