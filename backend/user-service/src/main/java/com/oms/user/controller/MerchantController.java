package com.oms.user.controller;

import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.PageResult;
import com.oms.common.core.result.Result;
import com.oms.user.dto.MerchantDtos.MerchantRegisterRequest;
import com.oms.user.dto.MerchantDtos.MerchantResponse;
import com.oms.user.dto.MerchantDtos.MerchantReviewRequest;
import com.oms.user.service.MerchantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/merchants")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @PostMapping("/register")
    public Result<Long> register(@RequestBody MerchantRegisterRequest request) {
        return Result.ok(merchantService.register(request));
    }

    @GetMapping
    public Result<PageResult<MerchantResponse>> page(
            @RequestHeader(value = "X-User-Type", required = false) Integer userType,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        requireAdmin(userType);
        return Result.ok(merchantService.page(keyword, status, page, size));
    }

    @PostMapping("/{id}/review")
    public Result<Void> review(
            @PathVariable Long id,
            @RequestBody MerchantReviewRequest request,
            @RequestHeader(value = "X-User-Type", required = false) Integer userType,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Username", required = false) String operatorName) {
        requireAdmin(userType);
        merchantService.review(id, request, operatorId, operatorName);
        return Result.ok();
    }

    private void requireAdmin(Integer userType) {
        if (userType == null || userType != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
