package com.oms.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IntegrationServiceApplicationTest {

    @Test
    void applicationClassIsLoadable() {
        assertThat(IntegrationServiceApplication.class).isNotNull();
    }
}
