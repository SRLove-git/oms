package com.oms.user.dto;

import java.time.LocalDateTime;

public final class AuditLogDtos {

    private AuditLogDtos() {
    }

    public record AuditLogResponse(
            Long id,
            Long operatorId,
            String operatorName,
            String module,
            String action,
            String bizId,
            String beforeData,
            String afterData,
            LocalDateTime createdAt) {
    }
}
