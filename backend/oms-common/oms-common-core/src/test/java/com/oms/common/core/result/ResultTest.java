package com.oms.common.core.result;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResultTest {

    @Test
    void okShouldReturnSuccess() {
        Result<String> result = Result.ok("data");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.code()).isEqualTo(ErrorCode.SUCCESS.getCode());
        assertThat(result.data()).isEqualTo("data");
    }

    @Test
    void failShouldReturnErrorCode() {
        Result<Void> result = Result.fail(ErrorCode.NOT_FOUND);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.code()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
        assertThat(result.message()).isEqualTo(ErrorCode.NOT_FOUND.getMessage());
    }
}
