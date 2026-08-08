package com.oms.common.core.result;

import java.io.Serializable;
import java.util.List;

/**
 * 统一分页响应。
 */
public record PageResult<T>(long total, List<T> records) implements Serializable {

    public static <T> PageResult<T> of(long total, List<T> records) {
        return new PageResult<>(total, records);
    }
}
