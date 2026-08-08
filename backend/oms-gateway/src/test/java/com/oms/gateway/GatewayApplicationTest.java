package com.oms.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GatewayApplicationTest {

    @Test
    void applicationClassIsLoadable() {
        assertThat(GatewayApplication.class).isNotNull();
    }
}
