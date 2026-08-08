package com.oms.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.PageResult;
import com.oms.integration.dto.IntegrationDtos.MessageResponse;
import com.oms.integration.dto.IntegrationDtos.SendRequest;
import com.oms.integration.dto.IntegrationDtos.TemplateRequest;
import com.oms.integration.dto.IntegrationDtos.TemplateResponse;
import com.oms.integration.entity.NotificationMessage;
import com.oms.integration.entity.NotificationTemplate;
import com.oms.integration.mapper.NotificationMessageMapper;
import com.oms.integration.mapper.NotificationTemplateMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Set<String> CHANNELS = Set.of("sms", "email", "in_app", "wechat");

    private final NotificationTemplateMapper templateMapper;
    private final NotificationMessageMapper messageMapper;

    public NotificationService(
            NotificationTemplateMapper templateMapper, NotificationMessageMapper messageMapper) {
        this.templateMapper = templateMapper;
        this.messageMapper = messageMapper;
    }

    @Transactional
    public TemplateResponse saveTemplate(TemplateRequest request) {
        if (request.code() == null || request.channel() == null || request.contentTemplate() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "code/channel/contentTemplate 必填");
        }
        NotificationTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<NotificationTemplate>()
                .eq(NotificationTemplate::getCode, request.code())
                .eq(NotificationTemplate::getChannel, request.channel())
                .eq(NotificationTemplate::getDeleted, 0)
                .last("LIMIT 1"));
        if (template == null) {
            template = new NotificationTemplate();
            template.setCode(request.code());
            template.setChannel(request.channel());
            template.setScene(request.scene());
            template.setName(request.name());
            template.setTitleTemplate(request.titleTemplate());
            template.setContentTemplate(request.contentTemplate());
            template.setStatus(request.status() == null ? 1 : request.status());
            templateMapper.insert(template);
        } else {
            template.setScene(request.scene());
            template.setName(request.name());
            template.setTitleTemplate(request.titleTemplate());
            template.setContentTemplate(request.contentTemplate());
            template.setStatus(request.status() == null ? template.getStatus() : request.status());
            templateMapper.updateById(template);
        }
        return toTemplateResponse(template);
    }

    public PageResult<TemplateResponse> pageTemplates(String scene, int page, int size) {
        LambdaQueryWrapper<NotificationTemplate> wrapper = new LambdaQueryWrapper<NotificationTemplate>()
                .eq(NotificationTemplate::getDeleted, 0)
                .orderByDesc(NotificationTemplate::getId);
        if (scene != null) {
            wrapper.eq(NotificationTemplate::getScene, scene);
        }
        Page<NotificationTemplate> result = templateMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(
                result.getTotal(), result.getRecords().stream().map(this::toTemplateResponse).toList());
    }

    @Transactional
    public MessageResponse send(SendRequest request) {
        String channel = request.channel() == null ? "in_app" : request.channel();
        if (!CHANNELS.contains(channel)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "不支持的通知渠道: " + channel);
        }
        if (request.receiver() == null || request.content() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "receiver 与 content 必填");
        }
        NotificationMessage message = new NotificationMessage();
        message.setMessageNo(generateNo("M"));
        message.setChannel(channel);
        message.setScene(request.scene());
        message.setReceiver(request.receiver());
        message.setTitle(request.title());
        message.setContent(request.content());
        if (request.receiver().startsWith("fail:")) {
            message.setStatus(2);
            message.setErrorMessage("模拟渠道发送失败");
        } else {
            message.setStatus(1);
            message.setSentAt(LocalDateTime.now());
        }
        messageMapper.insert(message);
        return toMessageResponse(message);
    }

    public PageResult<MessageResponse> pageMessages(
            String receiver, Integer status, int page, int size) {
        LambdaQueryWrapper<NotificationMessage> wrapper = new LambdaQueryWrapper<NotificationMessage>()
                .eq(NotificationMessage::getDeleted, 0)
                .orderByDesc(NotificationMessage::getId);
        if (receiver != null) {
            wrapper.eq(NotificationMessage::getReceiver, receiver);
        }
        if (status != null) {
            wrapper.eq(NotificationMessage::getStatus, status);
        }
        Page<NotificationMessage> result = messageMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(
                result.getTotal(), result.getRecords().stream().map(this::toMessageResponse).toList());
    }

    @Transactional
    public MessageResponse retry(Long messageId) {
        NotificationMessage message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (message.getStatus() == 1) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "消息已发送成功，无需重试");
        }
        if (message.getRetryCount() >= 3) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "重试次数已达上限");
        }
        message.setRetryCount(message.getRetryCount() + 1);
        if (message.getReceiver().startsWith("fail:")) {
            message.setErrorMessage("模拟渠道再次失败，retry=" + message.getRetryCount());
        } else {
            message.setStatus(1);
            message.setErrorMessage(null);
            message.setSentAt(LocalDateTime.now());
        }
        messageMapper.updateById(message);
        return toMessageResponse(message);
    }

    public List<MessageResponse> recentFailed(int limit) {
        List<NotificationMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<NotificationMessage>()
                .eq(NotificationMessage::getStatus, 2)
                .eq(NotificationMessage::getDeleted, 0)
                .orderByAsc(NotificationMessage::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 100)));
        return messages.stream().map(this::toMessageResponse).toList();
    }

    private String generateNo(String prefix) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return prefix + ts + ThreadLocalRandom.current().nextInt(10000, 100000);
    }

    private TemplateResponse toTemplateResponse(NotificationTemplate template) {
        return new TemplateResponse(
                template.getId(),
                template.getCode(),
                template.getName(),
                template.getChannel(),
                template.getScene(),
                template.getTitleTemplate(),
                template.getContentTemplate(),
                template.getStatus(),
                template.getUpdatedAt());
    }

    private MessageResponse toMessageResponse(NotificationMessage message) {
        return new MessageResponse(
                message.getId(),
                message.getMessageNo(),
                message.getChannel(),
                message.getScene(),
                message.getReceiver(),
                message.getTitle(),
                message.getContent(),
                message.getStatus(),
                message.getRetryCount(),
                message.getErrorMessage(),
                message.getSentAt(),
                message.getCreatedAt());
    }
}
