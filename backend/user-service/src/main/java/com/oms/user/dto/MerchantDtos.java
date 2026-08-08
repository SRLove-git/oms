package com.oms.user.dto;

import java.time.LocalDateTime;

public final class MerchantDtos {

    private MerchantDtos() {
    }

    public record MerchantRegisterRequest(
            String name,
            String contactName,
            String contactPhone,
            String username,
            String password) {
    }

    public record MerchantReviewRequest(boolean approved, String reason) {
    }

    public record MerchantResponse(
            Long id,
            String merchantNo,
            String name,
            String contactName,
            String contactPhone,
            Integer status,
            LocalDateTime createdAt) {
    }
}
