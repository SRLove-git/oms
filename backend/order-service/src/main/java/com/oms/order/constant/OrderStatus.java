package com.oms.order.constant;

import java.util.Map;
import java.util.Set;

public final class OrderStatus {

    public static final int PENDING_PAYMENT = 1;
    public static final int PAID = 2;
    public static final int AUDITED = 3;
    public static final int SHIPPED = 4;
    public static final int SIGNED = 5;
    public static final int COMPLETED = 6;
    public static final int CANCELLED = 7;
    public static final int AFTER_SALES = 8;

    private static final Map<Integer, Set<Integer>> ALLOWED_TRANSITIONS = Map.of(
            PENDING_PAYMENT, Set.of(PAID, CANCELLED),
            PAID, Set.of(AUDITED, CANCELLED),
            AUDITED, Set.of(SHIPPED),
            SHIPPED, Set.of(SIGNED, AFTER_SALES),
            SIGNED, Set.of(COMPLETED, AFTER_SALES),
            COMPLETED, Set.of(),
            CANCELLED, Set.of(),
            AFTER_SALES, Set.of(COMPLETED, CANCELLED));

    private OrderStatus() {
    }

    public static boolean canTransit(int from, int to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static String name(int status) {
        return switch (status) {
            case PENDING_PAYMENT -> "待支付";
            case PAID -> "已支付";
            case AUDITED -> "已审核";
            case SHIPPED -> "已发货";
            case SIGNED -> "已签收";
            case COMPLETED -> "已完成";
            case CANCELLED -> "已取消";
            case AFTER_SALES -> "售后处理中";
            default -> "未知";
        };
    }
}
