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

    @Test
    void shouldAllowBoundaryTransitions() {
        // 已支付 -> 已取消（管理员取消）
        assertThat(OrderStatus.canTransit(OrderStatus.PAID, OrderStatus.CANCELLED)).isTrue();
        // 已发货/已签收 -> 售后介入
        assertThat(OrderStatus.canTransit(OrderStatus.SHIPPED, OrderStatus.AFTER_SALES)).isTrue();
        assertThat(OrderStatus.canTransit(OrderStatus.SIGNED, OrderStatus.AFTER_SALES)).isTrue();
        // 售后处理中 -> 完成 / 取消
        assertThat(OrderStatus.canTransit(OrderStatus.AFTER_SALES, OrderStatus.COMPLETED)).isTrue();
        assertThat(OrderStatus.canTransit(OrderStatus.AFTER_SALES, OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void shouldRejectIllegalSkipsAndTerminalTransitions() {
        // 跳级流转全部拒绝
        assertThat(OrderStatus.canTransit(OrderStatus.PENDING_PAYMENT, OrderStatus.AUDITED)).isFalse();
        assertThat(OrderStatus.canTransit(OrderStatus.PAID, OrderStatus.SHIPPED)).isFalse();
        assertThat(OrderStatus.canTransit(OrderStatus.AUDITED, OrderStatus.COMPLETED)).isFalse();
        assertThat(OrderStatus.canTransit(OrderStatus.SHIPPED, OrderStatus.COMPLETED)).isFalse();
        assertThat(OrderStatus.canTransit(OrderStatus.SIGNED, OrderStatus.CANCELLED)).isFalse();
        // 终态不可再流转
        assertThat(OrderStatus.canTransit(OrderStatus.COMPLETED, OrderStatus.AFTER_SALES)).isFalse();
        assertThat(OrderStatus.canTransit(OrderStatus.CANCELLED, OrderStatus.PENDING_PAYMENT)).isFalse();
        assertThat(OrderStatus.canTransit(OrderStatus.CANCELLED, OrderStatus.PAID)).isFalse();
        // 未知状态
        assertThat(OrderStatus.canTransit(99, OrderStatus.PAID)).isFalse();
        assertThat(OrderStatus.canTransit(OrderStatus.PAID, 99)).isFalse();
    }

    @Test
    void shouldNameAllStatuses() {
        assertThat(OrderStatus.name(OrderStatus.PENDING_PAYMENT)).isEqualTo("待支付");
        assertThat(OrderStatus.name(OrderStatus.PAID)).isEqualTo("已支付");
        assertThat(OrderStatus.name(OrderStatus.AUDITED)).isEqualTo("已审核");
        assertThat(OrderStatus.name(OrderStatus.SHIPPED)).isEqualTo("已发货");
        assertThat(OrderStatus.name(OrderStatus.SIGNED)).isEqualTo("已签收");
        assertThat(OrderStatus.name(OrderStatus.COMPLETED)).isEqualTo("已完成");
        assertThat(OrderStatus.name(OrderStatus.CANCELLED)).isEqualTo("已取消");
        assertThat(OrderStatus.name(OrderStatus.AFTER_SALES)).isEqualTo("售后处理中");
        assertThat(OrderStatus.name(99)).isEqualTo("未知");
    }
}
