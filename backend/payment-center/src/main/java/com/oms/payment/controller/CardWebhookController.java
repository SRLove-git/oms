package com.oms.payment.controller;

import com.oms.payment.psp.CardPspClient;
import com.oms.payment.psp.CardWebhookEvent;
import com.oms.payment.service.PaymentService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 国际卡 PSP 签名回调入口（外部渠道调用，验签 + 幂等，无 JWT）。
 */
@RestController
@RequestMapping("/api/v1/payment-callbacks")
public class CardWebhookController {

    private final CardPspClient cardPspClient;
    private final PaymentService paymentService;

    public CardWebhookController(CardPspClient cardPspClient, PaymentService paymentService) {
        this.cardPspClient = cardPspClient;
        this.paymentService = paymentService;
    }

    @PostMapping("/card/webhook")
    public Map<String, String> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        CardWebhookEvent event = cardPspClient.parseWebhook(payload, signature);
        paymentService.handleCardWebhook(event);
        return Map.of("received", "true");
    }
}
