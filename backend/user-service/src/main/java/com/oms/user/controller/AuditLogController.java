package com.oms.user.controller;

import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.PageResult;
import com.oms.common.core.result.Result;
import com.oms.user.dto.AuditLogDtos.AuditLogResponse;
import com.oms.user.service.AuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public Result<PageResult<AuditLogResponse>> page(
            @RequestHeader(value = "X-User-Type", required = false) Integer userType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (userType == null || userType != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return Result.ok(auditService.page(page, size));
    }
}
