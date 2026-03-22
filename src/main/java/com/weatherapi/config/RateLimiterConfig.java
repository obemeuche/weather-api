package com.weatherapi.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

@Configuration
public class RateLimiterConfig {

    /**
     * Redis-backed ProxyManager for distributed rate limiting.
     *
     * Uses the Lettuce client that Spring Data Redis already has on the classpath —
     * no second Redis connection or client library needed. We open a dedicated
     * StatefulRedisConnection<String, byte[]> because Bucket4j stores bucket state
     * as raw bytes while keys remain human-readable strings (e.g. "rl:192.168.1.1").
     *
     * withExpirationAfterWrite(2 minutes): Redis TTL on bucket keys. Set to 2x the
     * refill window so that idle clients' keys are cleaned up automatically, preventing
     * unbounded key growth in Redis.
     *
     * All replicas share the same Redis counters → true 20 req/min per IP across the fleet.
     */
    @Bean
    public LettuceBasedProxyManager<String> rateLimitProxyManager(
            LettuceConnectionFactory connectionFactory) {

        RedisClient redisClient = (RedisClient) connectionFactory.getNativeClient();
        StatefulRedisConnection<String, byte[]> connection = redisClient.connect(
                RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)
        );

        // basedOnTimeForRefillingBucketUpToMax: Redis TTL = time to fully refill the bucket
        // (1 minute). Idle clients' keys are cleaned up automatically — no unbounded key growth.
        return LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
                .build();
    }

    /**
     * Shared bucket policy: 20 requests per minute, greedy refill (≈1 token / 3 sec).
     *
     * Defined as a bean so it is constructed once and reused on every request,
     * rather than being rebuilt per call in the controller.
     */
    @Bean
    public BucketConfiguration rateLimitConfiguration() {
        Refill refill = Refill.greedy(20, Duration.ofMinutes(1));
        Bandwidth limit = Bandwidth.classic(20, refill);
        return BucketConfiguration.builder()
                .addLimit(limit)
                .build();
    }
}
