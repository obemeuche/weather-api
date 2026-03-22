package com.weatherapi.service;

import com.weatherapi.client.VisualCrossingClient;
import com.weatherapi.model.WeatherResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestration layer for the weather use case.
 *
 * Flow:
 *   1. Check Redis cache via CacheService
 *   2. On HIT  → return cached WeatherResponse immediately
 *   3. On MISS → delegate to VisualCrossingClient for a live fetch
 *   4. Store the live response in cache with TTL
 *   5. Return the response
 *
 * This class knows nothing about HTTP, Redis internals, or Visual Crossing
 * URL structure — those concerns belong to CacheService and VisualCrossingClient
 * respectively. That separation means this orchestration logic can be reused
 * from any entry point (REST controller, scheduled job, CLI runner, etc.).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    private final CacheService cacheService;
    private final VisualCrossingClient visualCrossingClient;

    /**
     * Returns weather data for the given city, using Redis as a look-aside cache.
     *
     * Cache HITs and MISSes are logged at INFO level so that production monitoring
     * can track cache efficiency and Visual Crossing API call volume.
     *
     * @param city city name (any case — CacheService normalises the key)
     * @return WeatherResponse from cache or from Visual Crossing
     */
    public WeatherResponse getWeather(String city) {
        return cacheService.get(city)
                .map(cached -> {
                    log.info("Cache HIT for city: '{}'", city);
                    return cached;
                })
                .orElseGet(() -> {
                    log.info("Cache MISS for city: '{}' — fetching from Visual Crossing", city);
                    WeatherResponse response = visualCrossingClient.fetchWeather(city);
                    cacheService.put(city, response);
                    return response;
                });
    }
}
