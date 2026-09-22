package com.iamamansid.urlshortener.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MutablePropertySources;

class DatabaseUrlEnvironmentPostProcessorTest {

    private final DatabaseUrlEnvironmentPostProcessor processor = new DatabaseUrlEnvironmentPostProcessor();

    @Test
    void convertsRenderStylePostgresUrlToJdbc() {
        Map<String, Object> props = DatabaseUrlEnvironmentPostProcessor.convert(
                "postgres://myuser:mypass@db-host.example.com:5432/mydb");

        assertEquals("jdbc:postgresql://db-host.example.com:5432/mydb", props.get("spring.datasource.url"));
        assertEquals("myuser", props.get("spring.datasource.username"));
        assertEquals("mypass", props.get("spring.datasource.password"));
    }

    @Test
    void handlesUrlEncodedCredentialsAndQueryParams() {
        Map<String, Object> props = DatabaseUrlEnvironmentPostProcessor.convert(
                "postgres://user%40x:p%40ss@h:5432/db?sslmode=require");

        assertEquals("jdbc:postgresql://h:5432/db?sslmode=require", props.get("spring.datasource.url"));
        assertEquals("user@x", props.get("spring.datasource.username"));
        assertEquals("p@ss", props.get("spring.datasource.password"));
    }

    @Test
    void ignoresNonPostgresUrls() {
        assertTrue(DatabaseUrlEnvironmentPostProcessor.convert(
                "jdbc:postgresql://h:5432/db").isEmpty());
        assertTrue(DatabaseUrlEnvironmentPostProcessor.convert(
                "mysql://u:p@h:3306/db").isEmpty());
    }

    @Test
    void postProcessorSkipsBlankOrJdbcDatabaseUrl() {
        ConfigurableEnvironment env = new StandardEnvironment();
        MutablePropertySources sources = env.getPropertySources();

        processor.postProcessEnvironment(env, new SpringApplication());
        assertFalse(sources.contains(DatabaseUrlEnvironmentPostProcessor.SOURCE_NAME));

        sources.addFirst(new MapPropertySource("test",
                Map.of("DATABASE_URL", "jdbc:postgresql://h:5432/db")));
        processor.postProcessEnvironment(env, new SpringApplication());
        assertFalse(sources.contains(DatabaseUrlEnvironmentPostProcessor.SOURCE_NAME));
    }

    @Test
    void postProcessorRegistersConvertedPropertiesFirst() {
        ConfigurableEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("DATABASE_URL", "postgres://u:p@h:5432/db")));

        processor.postProcessEnvironment(env, new SpringApplication());

        assertEquals("jdbc:postgresql://h:5432/db", env.getProperty("spring.datasource.url"));
        assertEquals("u", env.getProperty("spring.datasource.username"));
        assertEquals("p", env.getProperty("spring.datasource.password"));
    }

    @Test
    void registeredAsEnvironmentPostProcessor() {
        assertTrue(processor instanceof EnvironmentPostProcessor);
    }
}
