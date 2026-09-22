package com.iamamansid.urlshortener.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.net.URI;

/**
 * Redis connection. Prefers a full {@code REDIS_URL} (e.g.
 * {@code redis://:password@host:6379/0} or {@code rediss://user:password@host:6379/0}
 * from a managed provider) and falls back to {@code REDIS_HOST} /
 * {@code REDIS_PORT} / {@code REDIS_PASSWORD}.
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
        boolean useSsl = false;
        if (!redisUrl.isBlank()) {
            useSsl = applyRedisUrl(config, redisUrl.trim());
        } else {
            config.setHostName(redisHost);
            config.setPort(redisPort);
            if (!redisPassword.isBlank()) {
                config.setPassword(redisPassword);
            }
        }
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.setUseSsl(useSsl);
        return factory;
    }

    /**
     * Parses a {@code redis://} or {@code rediss://} URL into the standalone
     * configuration. Returns {@code true} when TLS ({@code rediss://}) was
     * requested.
     */
    static boolean applyRedisUrl(RedisStandaloneConfiguration config, String redisUrl) {
        boolean useSsl = redisUrl.regionMatches(true, 0, "rediss://", 0, "rediss://".length());
        URI uri = URI.create(redisUrl);
        config.setHostName(uri.getHost());
        config.setPort(uri.getPort() == -1 ? 6379 : uri.getPort());
        String userInfo = uri.getUserInfo(); // "user:password", ":password" or "password"
        if (userInfo != null && !userInfo.isBlank()) {
            int sep = userInfo.indexOf(':');
            String username = sep >= 0 ? userInfo.substring(0, sep) : "";
            String password = sep >= 0 ? userInfo.substring(sep + 1) : userInfo;
            if (!username.isBlank()) {
                config.setUsername(username);
            }
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
        return useSsl;
    }
}
