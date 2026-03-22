package com.weatherapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * Declares a RedisTemplate<String, String> with explicit String serializers for
     * both keys and values. This replaces Spring Boot's default JdkSerializationRedisSerializer,
     * which produces unreadable binary blobs and causes ClassCastException when you
     * retrieve a value expecting a plain String.
     *
     * With StringRedisSerializer:
     *  - Keys are stored as plain UTF-8 strings  → redis-cli GET london works
     *  - Values are stored as JSON strings        → human-readable, cross-language compatible
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);

        return template;
    }
}
