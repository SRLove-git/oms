package com.oms.integration.jtexpress;

import com.oms.integration.jtexpress.JtExpressDtos.Activity;
import com.oms.integration.jtexpress.JtExpressDtos.TrackingResponse;
import com.oms.integration.provider.LogisticsProvider;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JtExpressLogisticsProvider implements LogisticsProvider {

    private final JtExpressProperties properties;
    private final JtExpressClient client;

    public JtExpressLogisticsProvider(JtExpressProperties properties, JtExpressClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public List<String> query(String trackingNo, String carrier) {
        if (!isConfigured() || !isJtExpress(carrier)) {
            return List.of();
        }
        TrackingResponse tracking = client.track(trackingNo);
        List<String> traces = new ArrayList<>();
        if (tracking.activities() != null) {
            for (Activity activity : tracking.activities()) {
                StringBuilder line = new StringBuilder();
                if (hasText(activity.createdAt())) {
                    line.append(activity.createdAt()).append(" ");
                }
                line.append(activity.name());
                if (hasText(activity.reason())) {
                    line.append(" - ").append(activity.reason());
                }
                traces.add(line.toString());
            }
        }
        return traces;
    }

    public static boolean isJtExpress(String carrier) {
        if (carrier == null) {
            return false;
        }
        String normalized = carrier.replace("&", "").replace(" ", "").replace("-", "").toUpperCase();
        return normalized.equals("JT") || normalized.equals("JTEXPRESS") || normalized.equals("JTEXPRESSSG");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
