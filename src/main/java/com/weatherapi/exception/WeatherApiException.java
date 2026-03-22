package com.weatherapi.exception;

import org.springframework.http.HttpStatus;

/**
 * Domain exception for all weather-related failures.
 *
 * Extends RuntimeException so it propagates freely through the service and
 * client layers without polluting method signatures with checked-exception
 * declarations. GlobalExceptionHandler catches it centrally.
 *
 * Carries an HttpStatus so the handler can return the most semantically
 * correct HTTP response code — e.g. 404 for an unknown city, 502 for an
 * upstream Visual Crossing failure, 429 for rate limiting at the API level.
 */
public class WeatherApiException extends RuntimeException {

    private final HttpStatus status;

    public WeatherApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public WeatherApiException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
