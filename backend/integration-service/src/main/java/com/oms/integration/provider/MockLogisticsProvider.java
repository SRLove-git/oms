package com.oms.integration.provider;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MockLogisticsProvider implements LogisticsProvider {

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public List<String> query(String trackingNo, String carrier) {
        return List.of();
    }
}
