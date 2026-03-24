package com.weatherapi.config;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RateLimiterConfigTest {

    @Mock private LettuceConnectionFactory mockFactory;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private RedisClient mockRedisClient;

    private RateLimiterConfig config;

    @BeforeEach
    void setUp() {
        config = new RateLimiterConfig();
    }

    @Test
    void rateLimitConfiguration_hasCapacityOfTwenty() {
        BucketConfiguration bucketConfig = config.rateLimitConfiguration();

        assertThat(bucketConfig).isNotNull();
        assertThat(bucketConfig.getBandwidths()).hasSize(1);
        assertThat(bucketConfig.getBandwidths()[0].getCapacity()).isEqualTo(20);
    }

    @Test
    void rateLimitProxyManager_createsProxyManagerFromConnectionFactory() {
        doReturn(mockRedisClient).when(mockFactory).getNativeClient();

        LettuceBasedProxyManager<String> result = config.rateLimitProxyManager(mockFactory);

        assertThat(result).isNotNull();
        verify(mockFactory).getNativeClient();
    }
}
