package com.iamamansid.urlshortener.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.net.URI;

/**
 * Redis connection. Prefers a full {@code REDIS_URL} (e.g.
 * {@code redis://:password@host:6379/0} from a managed provider) and falls
 * back to {@code REDIS_HOST} / {@code REDIS_PORT} / {@code REDIS_PASSWORD}.
 */
@Configuration
public class RedisConfig {

    @Value("${REDIS_URL:}")
    private String redisUrl;

    @Value("${REDIS_HOST:localhost}")
    private String redisHost;

    @Value("${REDIS_PORT:6379}")
    private int redisPort;

    @Value("${REDIS_PASSWORD:}")
    private String redisPassword;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        if (!redisUrl.isBlank()) {
            applyRedisUrl(config, redisUrl.trim());
        } else {
            config.setHostName(redisHost);
            config.setPort(redisPort);
            if (!redisPassword.isBlank()) {
                config.setPassword(redisPassword);
            }
        }
        return new LettuceConnectionFactory(config);
    }

    private static void applyRedisUrl(RedisStandaloneConfiguration config, String redisUrl) {
        URI uri = URI.create(redisUrl);
        config.setHostName(uri.getHost());
        config.setPort(uri.getPort() == -1 ? 6379 : uri.getPort());
        String userInfo = uri.getUserInfo(); // "password" or ":password"
        if (userInfo != null && !userInfo.isBlank()) {
            String password = userInfo.startsWith(":") ? userInfo.substring(1) : userInfo;
            if (!password.isBlank()) {
                config.setPassword(password);
            }
        }
        String path = uri.getPath(); // "/0"
        if (path != null && path.length() > 1) {
            try {
                config.setDatabase(Integer.parseInt(path.substring(1)));
            } catch (NumberFormatException ignored) {
                // keep default database 0
            }
        }
    }
}
