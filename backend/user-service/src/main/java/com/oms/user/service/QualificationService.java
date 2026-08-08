package com.oms.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.PageResult;
import com.oms.user.dto.QualificationDtos.QualificationCreateRequest;
import com.oms.user.dto.QualificationDtos.QualificationResponse;
import com.oms.user.dto.QualificationDtos.QualificationReviewRequest;
import com.oms.user.entity.Qualification;
import com.oms.user.mapper.QualificationMapper;
import org.springframework.stereotype.Service;

@Service
public class QualificationService {

    private final QualificationMapper qualificationMapper;
    private final AuditService auditService;

    public QualificationService(QualificationMapper qualificationMapper, AuditService auditService) {
        this.qualificationMapper = qualificationMapper;
        this.auditService = auditService;
    }

    public Long create(QualificationCreateRequest request, Long operatorId, String operatorName) {
        if (request.merchantId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "merchantId 不能为空");
        }
        Qualification qualification = new Qualification();
        qualification.setMerchantId(request.merchantId());
        qualification.setQualificationNo(request.qualificationNo());
        qualification.setQualificationType(request.qualificationType());
        qualification.setExpireAt(request.expireAt());
        qualification.setFileUrl(request.fileUrl());
        qualification.setStatus(1);
        qualificationMapper.insert(qualification);
        auditService.append(
                operatorId,
                operatorName,
                "qualification",
                "create",
                String.valueOf(qualification.getId()),
                null,
                request.qualificationNo());
        return qualification.getId();
    }

    public PageResult<QualificationResponse> page(Long merchantId, Integer status, int page, int size) {
        LambdaQueryWrapper<Qualification> wrapper = new LambdaQueryWrapper<Qualification>()
                .eq(Qualification::getDeleted, 0)
                .orderByDesc(Qualification::getId);
        if (merchantId != null) {
            wrapper.eq(Qualification::getMerchantId, merchantId);
        }
        if (status != null) {
            wrapper.eq(Qualification::getStatus, status);
        }
        Page<Qualification> result = qualificationMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList());
    }

    public void review(Long qualificationId, QualificationReviewRequest request, Long operatorId, String operatorName) {
        Qualification qualification = qualificationMapper.selectById(qualificationId);
        if (qualification == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        int before = qualification.getStatus();
        int after = request.approved() ? 2 : 3;
        qualification.setStatus(after);
        qualificationMapper.updateById(qualification);
        auditService.append(
                operatorId,
                operatorName,
                "qualification",
                "review",
                String.valueOf(qualificationId),
                String.valueOf(before),
                after + (request.reason() == null ? "" : ":" + request.reason()));
    }

    private QualificationResponse toResponse(Qualification qualification) {
        return new QualificationResponse(
                qualification.getId(),
                qualification.getMerchantId(),
                qualification.getQualificationNo(),
                qualification.getQualificationType(),
                qualification.getExpireAt(),
                qualification.getFileUrl(),
                qualification.getStatus(),
                qualification.getCreatedAt());
    }
}
