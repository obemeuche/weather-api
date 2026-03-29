package com.weatherapi.service;

import com.weatherapi.client.VisualCrossingClient;
import com.weatherapi.model.WeatherResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    @Mock private CacheService cacheService;
    @Mock private VisualCrossingClient visualCrossingClient;

    @InjectMocks
    private WeatherService weatherService;

    @Test
    void getWeather_returnsCachedResponse_onCacheHit() {
        WeatherResponse cached = new WeatherResponse();
        cached.setResolvedAddress("London, UK");

        when(cacheService.get("London")).thenReturn(Optional.of(cached));

        WeatherResponse result = weatherService.getWeather("London");

        assertThat(result).isEqualTo(cached);
        verify(visualCrossingClient, never()).fetchWeather("London");
        verify(cacheService, never()).put(any(), any());
    }

    @Test
    void getWeather_fetchesAndCaches_onCacheMiss() {
        WeatherResponse fetched = new WeatherResponse();
        fetched.setResolvedAddress("Paris, France");

        when(cacheService.get("Paris")).thenReturn(Optional.empty());
        when(visualCrossingClient.fetchWeather("Paris")).thenReturn(fetched);

        WeatherResponse result = weatherService.getWeather("Paris");

        assertThat(result).isEqualTo(fetched);
        verify(visualCrossingClient).fetchWeather("Paris");
        verify(cacheService).put("Paris", fetched);
    }
}
