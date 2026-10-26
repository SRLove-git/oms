package com.oms.integration.jtexpress;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class JtExpressSpringContextTest {

    @Test
    void createsClientThroughSpringConstructorInjection() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(JtExpressProperties.class);
            context.register(JtExpressConfiguration.class);
            context.register(JtExpressClient.class);
            context.refresh();

            assertThat(context.getBean(JtExpressClient.class)).isNotNull();
        }
    }
}
