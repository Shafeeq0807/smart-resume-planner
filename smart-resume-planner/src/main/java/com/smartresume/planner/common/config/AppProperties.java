package com.smartresume.planner.common.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code app.*} block in application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Security security,
        Storage storage,
        Upload upload,
        Matching matching,
        Github github,
        RateLimit rateLimit,
        Ai ai
) {

    public record Security(Jwt jwt, Cors cors) { }

    public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) { }

    public record Cors(List<String> allowedOrigins) { }

    public record Storage(String type, String localRoot) { }

    public record Upload(List<String> allowedTypes, int maxExtractedChars, Duration parseTimeout) { }

    public record Matching(Weights weights) {
        public record Weights(double hard, double soft, double semantic, double title) { }
    }

    public record Github(String apiBaseUrl, String token, int maxRepos, Duration cacheTtl) { }

    public record RateLimit(int aiCallsPerHour, int authAttemptsPerMinute) { }

    /** Label stored in ai_usage_logs; each Spring profile sets its own provider name. */
    public record Ai(String provider) { }
}
