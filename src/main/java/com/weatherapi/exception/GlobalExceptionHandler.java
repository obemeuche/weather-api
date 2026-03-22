package com.weatherapi.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Map;

/**
 * Centralised exception handler for all controllers.
 *
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody — every handler method
 * in this class automatically serialises its return value as JSON without needing
 * @ResponseBody on each method individually.
 *
 * Design rules:
 *   - WeatherApiException carries a known status and a safe message → return it as-is
 *   - Validation failures → 400 with the constraint message
 *   - All other exceptions → log the full detail server-side, return a vague 500 to the
 *     client (prevents information leakage of internal class names / stack traces)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles our domain exception. The message was written by us and is safe to expose.
     * The status code was set at the throw site to the most semantically correct value.
     */
    @ExceptionHandler(WeatherApiException.class)
    public ResponseEntity<Map<String, Object>> handleWeatherApiException(WeatherApiException e) {
        log.warn("WeatherApiException: status={} message={}", e.getStatus(), e.getMessage());
        return ResponseEntity
                .status(e.getStatus())
                .body(errorBody(e.getStatus().value(), e.getStatus().getReasonPhrase(), e.getMessage()));
    }

    /**
     * Handles @NotBlank / @Pattern violations on @PathVariable and @RequestParam.
     * ConstraintViolationException is thrown by Spring when @Validated + JSR-380
     * annotations are used on controller method parameters.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .findFirst()
                .orElse("Invalid request parameter");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorBody(400, "Bad Request", message));
    }

    /**
     * Handles path variable type mismatches (e.g. passing a string where an int is expected).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "Invalid value '" + e.getValue() + "' for parameter '" + e.getName() + "'";
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorBody(400, "Bad Request", message));
    }

    /**
     * Catch-all for any exception not handled above.
     *
     * IMPORTANT: we log the full exception (for ops) but return only a generic message
     * to the client. Returning e.getMessage() risks leaking internal details such as
     * database URLs, class names, or stack trace fragments (OWASP A05 – Security Misconfiguration).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(500, "Internal Server Error", "An unexpected error occurred. Please try again later."));
    }

    private Map<String, Object> errorBody(int status, String error, String message) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status", status,
                "error", error,
                "message", message
        );
    }
}
