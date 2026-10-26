package com.oms.integration.provider;

import com.oms.integration.jtexpress.JtExpressLogisticsProvider;
import java.util.List;
import org.springframework.stereotype.Component;

@Component("routingLogisticsProvider")
public class RoutingLogisticsProvider implements LogisticsProvider {

    private final JtExpressLogisticsProvider jtExpress;
    private final HttpLogisticsProvider fallback;

    public RoutingLogisticsProvider(JtExpressLogisticsProvider jtExpress, HttpLogisticsProvider fallback) {
        this.jtExpress = jtExpress;
        this.fallback = fallback;
    }

    @Override
    public boolean isConfigured() {
        return jtExpress.isConfigured() || fallback.isConfigured();
    }

    @Override
    public List<String> query(String trackingNo, String carrier) {
        if (JtExpressLogisticsProvider.isJtExpress(carrier)) {
            return jtExpress.query(trackingNo, carrier);
        }
        return fallback.query(trackingNo, carrier);
    }
}
