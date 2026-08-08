package com.oms.common.core.security;

/**
 * JWT 载荷：用户身份与租户上下文。
 */
public record JwtClaims(Long userId, String username, Integer userType, Long merchantId) {
}
