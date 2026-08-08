package com.oms.common.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtUtilTest {

    private static final String SECRET = "oms-test-secret-key-0123456789-0123456789";

    @Test
    void shouldRoundTripClaims() {
        JwtClaims claims = new JwtClaims(42L, "admin", 1, 7L);
        String token = JwtUtil.generateToken(SECRET, claims, Duration.ofHours(1));

        JwtClaims parsed = JwtUtil.parseToken(SECRET, token);
        assertThat(parsed.userId()).isEqualTo(42L);
        assertThat(parsed.username()).isEqualTo("admin");
        assertThat(parsed.userType()).isEqualTo(1);
        assertThat(parsed.merchantId()).isEqualTo(7L);
    }

    @Test
    void shouldRejectTamperedToken() {
        JwtClaims claims = new JwtClaims(42L, "admin", 1, null);
        String token = JwtUtil.generateToken(SECRET, claims, Duration.ofHours(1));
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> JwtUtil.parseToken(SECRET, tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectWrongSecret() {
        JwtClaims claims = new JwtClaims(42L, "admin", 1, null);
        String token = JwtUtil.generateToken(SECRET, claims, Duration.ofHours(1));

        assertThatThrownBy(() -> JwtUtil.parseToken(SECRET + "-other", token))
                .isInstanceOf(JwtException.class);
    }
}
