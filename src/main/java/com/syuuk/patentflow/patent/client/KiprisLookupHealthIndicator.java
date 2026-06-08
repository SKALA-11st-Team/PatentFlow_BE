package com.syuuk.patentflow.patent.client;

import com.syuuk.patentflow.patent.config.PatentLookupProperties;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("kiprisLookup")
public class KiprisLookupHealthIndicator implements HealthIndicator {

    private final PatentLookupProperties properties;

    public KiprisLookupHealthIndicator(PatentLookupProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        List<String> keys = serviceKeys(properties.kipris());
        if (keys.isEmpty()) {
            return Health.unknown()
                    .withDetail("configured", false)
                    .withDetail("reason", "KIPRIS service key is not configured")
                    .build();
        }
        return Health.up()
                .withDetail("configured", true)
                .withDetail("keyCount", keys.size())
                .withDetail("quotaWindow", YearMonth.now().toString())
                .build();
    }

    private List<String> serviceKeys(PatentLookupProperties.Kipris kipris) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addKey(keys, kipris.serviceKey());
        if (kipris.serviceKeys() != null) {
            kipris.serviceKeys().forEach(key -> addKey(keys, key));
        }
        return List.copyOf(keys);
    }

    private void addKey(LinkedHashSet<String> keys, String key) {
        if (key != null && !key.isBlank()) {
            keys.add(key.trim());
        }
    }
}
