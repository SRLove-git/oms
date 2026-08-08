package com.oms.user.controller;

import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.PageResult;
import com.oms.common.core.result.Result;
import com.oms.user.dto.QualificationDtos.QualificationCreateRequest;
import com.oms.user.dto.QualificationDtos.QualificationResponse;
import com.oms.user.dto.QualificationDtos.QualificationReviewRequest;
import com.oms.user.service.QualificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/qualifications")
public class QualificationController {

    private final QualificationService qualificationService;

    public QualificationController(QualificationService qualificationService) {
        this.qualificationService = qualificationService;
    }

    @PostMapping
    public Result<Long> create(
            @RequestBody QualificationCreateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Username", required = false) String operatorName,
            @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId) {
        if (request.merchantId() == null && merchantId != null) {
            request = new QualificationCreateRequest(
                    merchantId,
                    request.qualificationNo(),
                    request.qualificationType(),
                    request.expireAt(),
                    request.fileUrl());
        }
        return Result.ok(qualificationService.create(request, operatorId, operatorName));
    }

    @GetMapping
    public Result<PageResult<QualificationResponse>> page(
            @RequestHeader(value = "X-User-Type", required = false) Integer userType,
            @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId,
            @RequestParam(required = false) Long merchantIdParam,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long filterMerchant = userType != null && userType == 1 ? merchantIdParam : merchantId;
        return Result.ok(qualificationService.page(filterMerchant, status, page, size));
    }

    @PostMapping("/{id}/review")
    public Result<Void> review(
            @PathVariable Long id,
            @RequestBody QualificationReviewRequest request,
            @RequestHeader(value = "X-User-Type", required = false) Integer userType,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Username", required = false) String operatorName) {
        if (userType == null || userType != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        qualificationService.review(id, request, operatorId, operatorName);
        return Result.ok();
    }
}
