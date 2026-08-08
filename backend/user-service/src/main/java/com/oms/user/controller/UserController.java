package com.oms.user.controller;

import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.common.core.result.PageResult;
import com.oms.common.core.result.Result;
import com.oms.user.dto.UserDtos.UserCreateRequest;
import com.oms.user.dto.UserDtos.UserResponse;
import com.oms.user.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Result<PageResult<UserResponse>> page(
            @RequestHeader(value = "X-User-Type", required = false) Integer userType,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        requireAdmin(userType);
        return Result.ok(userService.page(keyword, page, size));
    }

    @PostMapping
    public Result<Long> create(
            @RequestBody UserCreateRequest request,
            @RequestHeader(value = "X-User-Type", required = false) Integer userType,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Username", required = false) String operatorName) {
        requireAdmin(userType);
        return Result.ok(userService.createUser(request, operatorId, operatorName));
    }

    private void requireAdmin(Integer userType) {
        if (userType == null || userType != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
