package com.oms.common.core.exception;

import com.oms.common.core.result.ErrorCode;

/**
 * 业务异常：由业务代码主动抛出，全局异常处理器统一转换为 Result。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(ErrorCode.BAD_REQUEST.getCode(), message);
    }

    public BusinessException(ErrorCode errorCode) {
        this(errorCode.getCode(), errorCode.getMessage());
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
