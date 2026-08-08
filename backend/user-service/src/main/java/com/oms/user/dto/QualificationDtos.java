package com.oms.user.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class QualificationDtos {

    private QualificationDtos() {
    }

    public record QualificationCreateRequest(
            Long merchantId,
            String qualificationNo,
            Integer qualificationType,
            LocalDate expireAt,
            String fileUrl) {
    }

    public record QualificationReviewRequest(boolean approved, String reason) {
    }

    public record QualificationResponse(
            Long id,
            Long merchantId,
            String qualificationNo,
            Integer qualificationType,
            LocalDate expireAt,
            String fileUrl,
            Integer status,
            LocalDateTime createdAt) {
    }
}
