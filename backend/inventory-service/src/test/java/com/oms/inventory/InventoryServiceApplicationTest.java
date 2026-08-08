package com.oms.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InventoryServiceApplicationTest {

    @Test
    void applicationClassIsLoadable() {
        assertThat(InventoryServiceApplication.class).isNotNull();
    }
}
