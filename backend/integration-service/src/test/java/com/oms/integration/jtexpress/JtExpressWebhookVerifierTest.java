package com.oms.integration.jtexpress;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JtExpressWebhookVerifierTest {

    @Test
    void verifiesHmacSha1Signature() {
        JtExpressProperties properties = new JtExpressProperties();
        properties.setWebhookSecret("secret");
        JtExpressWebhookVerifier verifier = new JtExpressWebhookVerifier(properties);

        assertThat(verifier.verify(
                "762a562e-53ed-4548-906f-98dea9374711",
                "4abef6170030038795e86e77a9a841a557fbb634"))
                .isTrue();
        assertThat(verifier.verify("762a562e-53ed-4548-906f-98dea9374711", "bad"))
                .isFalse();
    }
}
