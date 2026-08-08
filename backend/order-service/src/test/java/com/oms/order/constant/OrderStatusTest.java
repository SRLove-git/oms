package com.oms.order.constant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderStatusTest {

    @Test
    void shouldAllowValidTransitions() {
        assertThat(OrderStatus.canTransit(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID)).isTrue();
        assertThat(OrderStatus.canTransit(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.canTransit(OrderStatus.PAID, OrderStatus.AUDITED)).isTrue();
        assertThat(OrderStatus.canTransit(OrderStatus.AUDITED, OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStatus.canTransit(OrderStatus.SHIPPED, OrderStatus.SIGNED)).isTrue();
        assertThat(OrderStatus.canTransit(OrderStatus.SIGNED, OrderStatus.COMPLETED)).isTrue();
    }

    @Test
    void shouldRejectInvalidTransitions() {
        assertThat(OrderStatus.canTransit(OrderStatus.PENDING_PAYMENT, OrderStatus.COMPLETED)).isFalse();
        assertThat(OrderStatus.canTransit(OrderStatus.COMPLETED, OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.canTransit(OrderStatus.AUDITED, OrderStatus.PAID)).isFalse();
    }
}
