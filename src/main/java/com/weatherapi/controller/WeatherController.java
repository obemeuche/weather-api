package com.weatherapi.controller;

import com.weatherapi.model.WeatherResponse;
import com.weatherapi.service.WeatherService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Entry point for weather requests. Keeps HTTP concerns here and delegates
 * all business logic to WeatherService.
 *
 * Rate limiting strategy:
 *   - One Bucket4j token-bucket per client IP, state stored in Redis
 *   - All replicas share the same counters → true 20 req/min per IP fleet-wide
 *   - LettuceBasedProxyManager.builder().build() atomically retrieves or creates
 *     the bucket for a given key; no local map or ApplicationContext needed
 *   - The controller is fully stateless and safe to scale horizontally
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;
    private final LettuceBasedProxyManager<String> rateLimitProxyManager;
    private final BucketConfiguration rateLimitConfiguration;

    /**
     * GET /api/v1/weather/{city}
     *
     * Returns current weather and forecast for the requested city.
     * Responds with 429 if the client IP exceeds 20 requests per minute.
     */
    @GetMapping("/{city}")
    public ResponseEntity<?> getWeather(
            @PathVariable @NotBlank(message = "City name must not be blank") String city,
            HttpServletRequest request) {

        String clientIp = resolveClientIp(request);
        String bucketKey = "rl:" + clientIp;

        // Retrieve (or lazily create) the Redis-backed bucket for this IP.
        // The supplier is only called on first access; subsequent calls return
        // the existing bucket whose state is stored in Redis.
        Bucket bucket = rateLimitProxyManager.builder()
                .build(bucketKey, () -> rateLimitConfiguration);

        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "error", "Too many requests",
                            "message", "Rate limit exceeded. Maximum 20 requests per minute."
                    ));
        }

        WeatherResponse response = weatherService.getWeather(city);
        return ResponseEntity.ok(response);
    }

    /**
     * Resolves the real client IP, honouring X-Forwarded-For when behind a proxy/load balancer.
     * Falls back to the direct remote address if the header is absent.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
