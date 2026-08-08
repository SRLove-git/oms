package com.oms.user.dto;

import java.time.LocalDateTime;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserCreateRequest(
            String username,
            String password,
            String realName,
            String phone,
            String email,
            Integer userType,
            Long merchantId) {
    }

    public record UserResponse(
            Long id,
            String username,
            String realName,
            String phone,
            String email,
            Integer userType,
            Long merchantId,
            Integer status,
            LocalDateTime createdAt) {
    }
}
