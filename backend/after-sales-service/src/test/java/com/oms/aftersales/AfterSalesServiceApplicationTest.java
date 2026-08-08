package com.oms.aftersales;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AfterSalesServiceApplicationTest {

    @Test
    void applicationClassIsLoadable() {
        assertThat(AfterSalesServiceApplication.class).isNotNull();
    }
}
