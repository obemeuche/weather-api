package com.weatherapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weatherapi.model.WeatherResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Owns all Redis read/write mechanics for weather data.
 *
 * Responsibilities:
 *   - Normalise cache keys (lowercase + trim)
 *   - Serialise WeatherResponse → JSON string for storage
 *   - Deserialise JSON string → WeatherResponse on retrieval
 *   - Apply TTL on every write
 *
 * Deliberately does NOT know about HTTP, Visual Crossing, or request routing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${weather.cache.ttl-hours}")
    private int ttlHours;

    /**
     * Looks up a city's weather in Redis.
     *
     * @param city raw city name from the request (any case, may have whitespace)
     * @return Optional containing the cached WeatherResponse, or empty on a cache miss
     */
    public Optional<WeatherResponse> get(String city) {
        String key = normalise(city);
        String json = redisTemplate.opsForValue().get(key);

        if (json == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(json, WeatherResponse.class));
        } catch (JsonProcessingException e) {
            // Corrupted cache entry — treat as a miss so it gets refreshed
            log.warn("Failed to deserialise cached entry for key '{}', treating as cache miss: {}", key, e.getMessage());
            redisTemplate.delete(key);
            return Optional.empty();
        }
    }

    /**
     * Stores a WeatherResponse in Redis with the configured TTL.
     *
     * Cache write failures are logged but do NOT propagate — a failed cache write
     * should degrade gracefully (uncached response) rather than failing the request.
     *
     * @param city     raw city name (will be normalised to the cache key)
     * @param response the WeatherResponse to cache
     */
    public void put(String city, WeatherResponse response) {
        String key = normalise(city);
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, json, Duration.ofHours(ttlHours));
            log.debug("Cached weather for '{}' with TTL {} hours", key, ttlHours);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialise WeatherResponse for key '{}', skipping cache write: {}", key, e.getMessage());
        }
    }

    /** Normalises a city name to a consistent Redis key format. */
    private String normalise(String city) {
        return city.trim().toLowerCase();
    }
}
