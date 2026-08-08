package com.oms.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.PageResult;
import com.oms.user.dto.UserDtos.UserCreateRequest;
import com.oms.user.dto.UserDtos.UserResponse;
import com.oms.user.entity.User;
import com.oms.user.mapper.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final AuditService auditService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserMapper userMapper, AuditService auditService) {
        this.userMapper = userMapper;
        this.auditService = auditService;
    }

    public Long createUser(UserCreateRequest request, Long operatorId, String operatorName) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.username())
                .eq(User::getDeleted, 0));
        if (exists > 0) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "用户名已存在");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRealName(request.realName());
        user.setPhone(request.phone());
        user.setEmail(request.email());
        user.setUserType(request.userType() == null ? 1 : request.userType());
        user.setMerchantId(request.merchantId());
        user.setStatus(1);
        userMapper.insert(user);
        auditService.append(operatorId, operatorName, "user", "create", String.valueOf(user.getId()), null, request.username());
        return user.getId();
    }

    public PageResult<UserResponse> page(String keyword, int page, int size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getDeleted, 0)
                .orderByDesc(User::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword).or().like(User::getRealName, keyword));
        }
        Page<User> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList());
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getPhone(),
                user.getEmail(),
                user.getUserType(),
                user.getMerchantId(),
                user.getStatus(),
                null);
    }
}
