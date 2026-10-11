package com.smartresume.planner.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** Brute-force guard: N attempts per minute per client IP on the unauthenticated auth endpoints. */
@Slf4j
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS =
            Set.of("/api/auth/login", "/api/auth/register", "/api/auth/refresh");

    private final int attemptsPerMinute;
    private final ObjectMapper objectMapper;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(50_000)
            .build();

    public AuthRateLimitFilter(int attemptsPerMinute, ObjectMapper objectMapper) {
        this.attemptsPerMinute = attemptsPerMinute;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getRemoteAddr() + "|" + request.getRequestURI();
        Bucket bucket = buckets.get(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(attemptsPerMinute)
                        .refillGreedy(attemptsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build());
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Auth rate limit hit for {} on {}", request.getRemoteAddr(), request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", "60");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "status", 429,
                "title", "Too Many Requests",
                "code", "RATE_LIMITED",
                "detail", "Too many attempts. Please wait a minute and try again."));
    }
}
