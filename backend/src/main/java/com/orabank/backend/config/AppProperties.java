package com.orabank.backend.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String timezone,
        String jwtSecret,
        Duration jwtExpiration,
        String jwtIssuer,
        List<String> corsOrigins,
        String frontendUrl,
        String mailFrom,
        String mailPreviewHost,
        int mailPreviewPort,
        Duration passwordSetupLinkValidity,
        boolean logPasswordSetupLinks) {
}
