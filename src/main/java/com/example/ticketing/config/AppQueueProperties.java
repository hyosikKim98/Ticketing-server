package com.example.ticketing.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.queue")
public record AppQueueProperties(
    Duration entryTokenTtl,
    Duration paymentGuardTtl,
    int maxActiveSlots,
    boolean testTokenEndpointEnabled
) {
}
