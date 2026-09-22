package com.iamamansid.urlshortener.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;

class RedisConfigTest {

    private RedisStandaloneConfiguration parse(String url) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        boolean ssl = RedisConfig.applyRedisUrl(config, url);
        return config;
    }

    @Test
    void parsesPlainRedisUrl() {
        RedisStandaloneConfiguration config = parse("redis://:s3cret@redis-host:6380/2");

        assertEquals("redis-host", config.getHostName());
        assertEquals(6380, config.getPort());
        assertEquals("s3cret", new String(config.getPassword().get()));
        assertEquals(2, config.getDatabase());
    }

    @Test
    void parsesUserPasswordFormWithoutSsl() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        boolean ssl = RedisConfig.applyRedisUrl(config, "redis://red-user:pw@h:6379");

        assertFalse(ssl);
        assertEquals("red-user", config.getUsername());
        assertEquals("pw", new String(config.getPassword().get()));
        assertEquals(6379, config.getPort());
        assertEquals(0, config.getDatabase());
    }

    @Test
    void redissUrlEnablesSsl() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        boolean ssl = RedisConfig.applyRedisUrl(config, "rediss://:pw@h:6379/0");

        assertTrue(ssl);
        assertEquals("h", config.getHostName());
        assertEquals("pw", new String(config.getPassword().get()));
    }
}
