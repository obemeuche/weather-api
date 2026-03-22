package com.weatherapi.client;

import com.weatherapi.exception.WeatherApiException;
import com.weatherapi.model.WeatherResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Anti-Corruption Layer for the Visual Crossing Weather API.
 *
 * Responsibilities:
 *   - Construct the correct request URI and query parameters
 *   - Execute the HTTP call and deserialise the response
 *   - Translate Visual Crossing HTTP errors into WeatherApiException
 *     with semantically correct HTTP status codes
 *
 * Nothing outside this class knows about Visual Crossing's URL structure,
 * query parameter names, or error response format.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisualCrossingClient {

    private final WebClient visualCrossingWebClient;

    @Value("${weather.api.key}")
    private String apiKey;

    /**
     * Fetches current weather and forecast for the given city.
     *
     * Uses .block() because this application runs on Spring MVC (Tomcat).
     * WebClient is used over RestTemplate for its better timeout handling,
     * exchange filter support, and because RestTemplate is in maintenance mode.
     *
     * @param city city name exactly as provided (normalisation happens in CacheService)
     * @return WeatherResponse deserialised from Visual Crossing's JSON
     * @throws WeatherApiException on 4xx (bad request / unknown city) or 5xx (upstream failure)
     */
    public WeatherResponse fetchWeather(String city) {
        log.info("Calling Visual Crossing API for city: '{}'", city);

        try {
            return visualCrossingWebClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{city}")
                            .queryParam("unitGroup", "metric")
                            .queryParam("key", apiKey)
                            .queryParam("contentType", "json")
                            .queryParam("include", "current,days")
                            .build(city))
                    .retrieve()
                    // 4xx errors — bad city name, invalid key, malformed request
                    .onStatus(HttpStatusCode::is4xxClientError, response ->
                            response.bodyToMono(String.class).map(body -> {
                                String message = response.statusCode().value() == 401
                                        ? "Invalid or missing Weather API key"
                                        : "City not found or invalid request: " + city;
                                return new WeatherApiException(message, HttpStatus.valueOf(response.statusCode().value()));
                            })
                    )
                    // 5xx errors — Visual Crossing is down or overloaded
                    .onStatus(HttpStatusCode::is5xxServerError, response ->
                            response.bodyToMono(String.class).map(body ->
                                    new WeatherApiException(
                                            "Weather data provider is temporarily unavailable. Please try again later.",
                                            HttpStatus.BAD_GATEWAY
                                    )
                            )
                    )
                    .bodyToMono(WeatherResponse.class)
                    .block();

        } catch (WeatherApiException e) {
            // Already wrapped — rethrow as-is
            throw e;
        } catch (WebClientResponseException e) {
            // Fallback for any response errors not caught by onStatus
            throw new WeatherApiException(
                    "Failed to fetch weather data for city: " + city,
                    HttpStatus.BAD_GATEWAY,
                    e
            );
        } catch (Exception e) {
            // Network errors, timeouts, DNS failures
            log.error("Unexpected error calling Visual Crossing for city '{}': {}", city, e.getMessage());
            throw new WeatherApiException(
                    "Unable to reach weather data provider. Please try again later.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    e
            );
        }
    }
}
