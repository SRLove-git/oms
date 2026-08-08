package com.oms.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderServiceApplicationTest {

    @Test
    void applicationClassIsLoadable() {
        assertThat(OrderServiceApplication.class).isNotNull();
    }
}
