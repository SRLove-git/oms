package com.oms.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.security.JwtClaims;
import com.oms.common.core.security.JwtUtil;
import com.oms.user.dto.AuthDtos.LoginRequest;
import com.oms.user.dto.AuthDtos.LoginResponse;
import com.oms.user.dto.AuthDtos.UserInfoResponse;
import com.oms.user.entity.User;
import com.oms.user.mapper.UserMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final AuditService auditService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${oms.security.jwt-secret}")
    private String jwtSecret;

    @Value("${oms.security.jwt-ttl-hours:24}")
    private long jwtTtlHours;

    public AuthService(UserMapper userMapper, AuditService auditService) {
        this.userMapper = userMapper;
        this.auditService = auditService;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.username())
                .eq(User::getDeleted, 0));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "账号已停用");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        auditService.append(
                user.getId(),
                user.getRealName() == null ? user.getUsername() : user.getRealName(),
                "auth",
                "login",
                String.valueOf(user.getId()),
                null,
                null);

        String token = JwtUtil.generateToken(
                jwtSecret,
                new JwtClaims(user.getId(), user.getUsername(), user.getUserType(), user.getMerchantId()),
                Duration.ofHours(jwtTtlHours));
        return new LoginResponse(token, toInfo(user));
    }

    public UserInfoResponse me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return toInfo(user);
    }

    private UserInfoResponse toInfo(User user) {
        return new UserInfoResponse(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getUserType(),
                user.getMerchantId(),
                user.getStatus(),
                List.of());
    }
}
