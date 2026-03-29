package com.weatherapi.service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weatherapi.model.WeatherResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheServiceTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cacheService, "ttlHours", 12);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void get_returnsCachedResponse_whenCacheHit() throws JsonProcessingException {
        WeatherResponse expected = new WeatherResponse();
        expected.setResolvedAddress("London, UK");

        when(valueOperations.get("london")).thenReturn("{\"resolvedAddress\":\"London, UK\"}");
        when(objectMapper.readValue(anyString(), eq(WeatherResponse.class))).thenReturn(expected);

        Optional<WeatherResponse> result = cacheService.get("London");

        assertThat(result).isPresent();
        assertThat(result.get().getResolvedAddress()).isEqualTo("London, UK");
    }

    @Test
    void get_returnsEmpty_whenCacheMiss() {
        when(valueOperations.get("london")).thenReturn(null);

        Optional<WeatherResponse> result = cacheService.get("  London  ");

        assertThat(result).isEmpty();
    }

    @Test
    void get_returnsEmpty_andDeletesKey_whenCachedJsonIsCorrupt() throws JsonProcessingException {
        when(valueOperations.get("london")).thenReturn("corrupt-json");
        when(objectMapper.readValue(anyString(), eq(WeatherResponse.class)))
                .thenThrow(mock(JsonParseException.class));

        Optional<WeatherResponse> result = cacheService.get("LONDON");

        assertThat(result).isEmpty();
        verify(redisTemplate).delete("london");
    }

    @Test
    void put_serialisesAndWritesToRedis_withConfiguredTtl() throws JsonProcessingException {
        WeatherResponse response = new WeatherResponse();
        when(objectMapper.writeValueAsString(response)).thenReturn("{\"resolvedAddress\":null}");

        cacheService.put("  London  ", response);

        verify(valueOperations).set("london", "{\"resolvedAddress\":null}", Duration.ofHours(12));
    }

    @Test
    void put_skipsWrite_whenSerialisationFails() throws JsonProcessingException {
        WeatherResponse response = new WeatherResponse();
        when(objectMapper.writeValueAsString(response)).thenThrow(mock(JsonProcessingException.class));

        cacheService.put("London", response);

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }
}
