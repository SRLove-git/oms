package com.oms.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentCenterApplicationTest {

    @Test
    void applicationClassIsLoadable() {
        assertThat(PaymentCenterApplication.class).isNotNull();
    }
}
