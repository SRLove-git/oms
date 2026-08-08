package com.oms.integration.controller;

import com.oms.common.core.result.PageResult;
import com.oms.common.core.result.Result;
import com.oms.integration.dto.IntegrationDtos.MessageResponse;
import com.oms.integration.dto.IntegrationDtos.SendRequest;
import com.oms.integration.dto.IntegrationDtos.TemplateRequest;
import com.oms.integration.dto.IntegrationDtos.TemplateResponse;
import com.oms.integration.service.NotificationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/templates")
    public Result<TemplateResponse> saveTemplate(@RequestBody TemplateRequest request) {
        return Result.ok(notificationService.saveTemplate(request));
    }

    @GetMapping("/templates")
    public Result<PageResult<TemplateResponse>> pageTemplates(
            @RequestParam(required = false) String scene,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(notificationService.pageTemplates(scene, page, size));
    }

    @PostMapping("/send")
    public Result<MessageResponse> send(@RequestBody SendRequest request) {
        return Result.ok(notificationService.send(request));
    }

    @GetMapping("/messages")
    public Result<PageResult<MessageResponse>> pageMessages(
            @RequestParam(required = false) String receiver,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(notificationService.pageMessages(receiver, status, page, size));
    }

    @GetMapping("/messages/failed")
    public Result<List<MessageResponse>> failed(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(notificationService.recentFailed(limit));
    }

    @PostMapping("/messages/{id}/retry")
    public Result<MessageResponse> retry(@PathVariable Long id) {
        return Result.ok(notificationService.retry(id));
    }
}
