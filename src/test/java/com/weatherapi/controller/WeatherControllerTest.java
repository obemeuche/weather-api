package com.weatherapi.controller;

import com.weatherapi.model.WeatherResponse;
import com.weatherapi.service.WeatherService;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WeatherController.class)
class WeatherControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WeatherService weatherService;

    @MockBean
    @SuppressWarnings("rawtypes")
    private LettuceBasedProxyManager rateLimitProxyManager;

    @MockBean
    private BucketConfiguration rateLimitConfiguration;

    private BucketProxy bucket;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        bucket = mock(BucketProxy.class);
        RemoteBucketBuilder<String> bucketBuilder = mock(RemoteBucketBuilder.class);
        when(rateLimitProxyManager.builder()).thenReturn(bucketBuilder);
        when(bucketBuilder.build(anyString(), any(java.util.function.Supplier.class))).thenReturn(bucket);
    }

    @Test
    void getWeather_returns200_whenRateLimitNotExceeded() throws Exception {
        WeatherResponse response = new WeatherResponse();
        response.setResolvedAddress("London, UK");

        when(bucket.tryConsume(1)).thenReturn(true);
        when(weatherService.getWeather("london")).thenReturn(response);

        mockMvc.perform(get("/api/v1/weather/london"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedAddress").value("London, UK"));
    }

    @Test
    void getWeather_returns429_whenRateLimitExceeded() throws Exception {
        when(bucket.tryConsume(1)).thenReturn(false);

        mockMvc.perform(get("/api/v1/weather/london")
                        .header("X-Forwarded-For", "192.168.1.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Too many requests"))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded. Maximum 20 requests per minute."));
    }

    @Test
    void getWeather_usesFirstIp_whenXForwardedForHasMultiple() throws Exception {
        WeatherResponse response = new WeatherResponse();
        when(bucket.tryConsume(1)).thenReturn(true);
        when(weatherService.getWeather("london")).thenReturn(response);

        mockMvc.perform(get("/api/v1/weather/london")
                        .header("X-Forwarded-For", "10.0.0.1, 10.0.0.2, 10.0.0.3"))
                .andExpect(status().isOk());
    }

    @Test
    void getWeather_usesRemoteAddr_whenXForwardedForIsBlank() throws Exception {
        WeatherResponse response = new WeatherResponse();
        when(bucket.tryConsume(1)).thenReturn(true);
        when(weatherService.getWeather("london")).thenReturn(response);

        mockMvc.perform(get("/api/v1/weather/london")
                        .header("X-Forwarded-For", "   "))
                .andExpect(status().isOk());
    }
}
