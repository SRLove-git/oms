package com.oms.user.dto;

import java.util.List;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(String username, String password) {
    }

    public record UserInfoResponse(
            Long id,
            String username,
            String realName,
            Integer userType,
            Long merchantId,
            Integer status,
            List<String> permissions) {
    }

    public record LoginResponse(String token, UserInfoResponse user) {
    }
}
