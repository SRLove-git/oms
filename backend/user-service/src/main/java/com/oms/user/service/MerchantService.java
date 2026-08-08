package com.oms.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.PageResult;
import com.oms.user.dto.MerchantDtos.MerchantRegisterRequest;
import com.oms.user.dto.MerchantDtos.MerchantResponse;
import com.oms.user.dto.MerchantDtos.MerchantReviewRequest;
import com.oms.user.entity.MerchantInfo;
import com.oms.user.entity.User;
import com.oms.user.mapper.MerchantInfoMapper;
import com.oms.user.mapper.UserMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MerchantService {

    private final MerchantInfoMapper merchantMapper;
    private final UserMapper userMapper;
    private final AuditService auditService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public MerchantService(
            MerchantInfoMapper merchantMapper, UserMapper userMapper, AuditService auditService) {
        this.merchantMapper = merchantMapper;
        this.userMapper = userMapper;
        this.auditService = auditService;
    }

    @Transactional
    public Long register(MerchantRegisterRequest request) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.username())
                .eq(User::getDeleted, 0));
        if (exists > 0) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "登录账号已存在");
        }

        MerchantInfo merchant = new MerchantInfo();
        merchant.setMerchantNo(generateMerchantNo());
        merchant.setName(request.name());
        merchant.setContactName(request.contactName());
        merchant.setContactPhone(request.contactPhone());
        merchant.setStatus(1);
        merchantMapper.insert(merchant);

        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRealName(request.contactName());
        user.setPhone(request.contactPhone());
        user.setUserType(2);
        user.setMerchantId(merchant.getId());
        user.setStatus(1);
        userMapper.insert(user);

        auditService.append(null, request.username(), "merchant", "register", String.valueOf(merchant.getId()), null, merchant.getName());
        return merchant.getId();
    }

    public PageResult<MerchantResponse> page(String keyword, Integer status, int page, int size) {
        LambdaQueryWrapper<MerchantInfo> wrapper = new LambdaQueryWrapper<MerchantInfo>()
                .eq(MerchantInfo::getDeleted, 0)
                .orderByDesc(MerchantInfo::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(MerchantInfo::getName, keyword).or().like(MerchantInfo::getMerchantNo, keyword));
        }
        if (status != null) {
            wrapper.eq(MerchantInfo::getStatus, status);
        }
        Page<MerchantInfo> result = merchantMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList());
    }

    public void review(Long merchantId, MerchantReviewRequest request, Long operatorId, String operatorName) {
        MerchantInfo merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        int before = merchant.getStatus();
        int after = request.approved() ? 2 : 3;
        merchant.setStatus(after);
        merchantMapper.updateById(merchant);
        auditService.append(
                operatorId,
                operatorName,
                "merchant",
                "review",
                String.valueOf(merchantId),
                String.valueOf(before),
                after + (request.reason() == null ? "" : ":" + request.reason()));
    }

    private String generateMerchantNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "M" + ts + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    private MerchantResponse toResponse(MerchantInfo merchant) {
        return new MerchantResponse(
                merchant.getId(),
                merchant.getMerchantNo(),
                merchant.getName(),
                merchant.getContactName(),
                merchant.getContactPhone(),
                merchant.getStatus(),
                merchant.getCreatedAt());
    }
}
