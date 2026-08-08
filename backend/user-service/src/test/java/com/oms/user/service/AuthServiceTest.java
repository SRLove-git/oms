package com.oms.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.oms.common.core.exception.BusinessException;
import com.oms.user.dto.AuthDtos.LoginRequest;
import com.oms.user.dto.AuthDtos.LoginResponse;
import com.oms.user.entity.User;
import com.oms.user.mapper.UserMapper;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {

    private UserMapper userMapper;
    private AuditService auditService;
    private AuthService authService;

    @BeforeEach
    void setUp() throws Exception {
        userMapper = mock(UserMapper.class);
        auditService = mock(AuditService.class);
        authService = new AuthService(userMapper, auditService);
        setField(authService, "jwtSecret", "oms-test-secret-key-0123456789-0123456789");
        setField(authService, "jwtTtlHours", 24L);
    }

    @Test
    void loginShouldReturnToken() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword(new BCryptPasswordEncoder().encode("admin123"));
        user.setUserType(1);
        user.setStatus(1);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        LoginResponse response = authService.login(new LoginRequest("admin", "admin123"));
        assertThat(response.token()).isNotBlank();
        assertThat(response.user().username()).isEqualTo("admin");
    }

    @Test
    void loginShouldRejectWrongPassword() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword(new BCryptPasswordEncoder().encode("admin123"));
        user.setStatus(1);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "wrong")))
                .isInstanceOf(BusinessException.class);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
