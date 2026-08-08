package com.oms.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserServiceApplicationTest {

    @Test
    void applicationClassIsLoadable() {
        assertThat(UserServiceApplication.class).isNotNull();
    }
}
