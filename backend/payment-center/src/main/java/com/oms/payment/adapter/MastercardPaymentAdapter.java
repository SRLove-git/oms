package com.oms.payment.adapter;

import org.springframework.stereotype.Component;

/**
 * Mastercard 国际卡渠道：真实 PSP 配置就绪后走托管结算页，否则回退模拟通道。
 */
@Component
public class MastercardPaymentAdapter extends AbstractCardPaymentAdapter {

    @Override
    public String channel() {
        return "mastercard";
    }
}
