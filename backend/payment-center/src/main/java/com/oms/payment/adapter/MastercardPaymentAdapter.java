package com.oms.payment.adapter;

import org.springframework.stereotype.Component;

/**
 * Mastercard 国际卡渠道（当前为模拟通道，真实 PSP 配置就绪后替换）。
 */
@Component
public class MastercardPaymentAdapter extends AbstractMockCardPaymentAdapter {

    @Override
    public String channel() {
        return "mastercard";
    }
}
