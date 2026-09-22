package com.iamamansid.urlshortener.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Discovers database credentials from the {@code DATABASE_URL} environment
 * variable and exposes them as standard Spring datasource properties.
 *
 * <p>Managed providers (Render, Heroku, Railway) inject {@code DATABASE_URL}
 * in {@code postgres://user:password@host:port/db} form, which is not a valid
 * JDBC URL. When such a value is detected it becomes the single source of
 * truth: it is converted to {@code jdbc:postgresql://host:port/db} and the
 * credentials are split out into {@code spring.datasource.username}/
 * {@code spring.datasource.password}, overriding the {@code application.yml}
 * placeholders. Values already in JDBC form (or absent) are left untouched,
 * in which case {@code DB_USERNAME}/{@code DB_PASSWORD} behave as before.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String SOURCE_NAME = "databaseUrlDiscovery";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return;
        }
        String trimmed = databaseUrl.trim();
        if (!trimmed.startsWith("postgres://") && !trimmed.startsWith("postgresql://")) {
            return;
        }
        Map<String, Object> props = convert(trimmed);
        if (!props.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, props));
        }
    }

    static Map<String, Object> convert(String databaseUrl) {
        Map<String, Object> props = new HashMap<>();
        String trimmed = databaseUrl == null ? "" : databaseUrl.trim();
        if (!trimmed.startsWith("postgres://") && !trimmed.startsWith("postgresql://")) {
            return props;
        }
        URI uri;
        try {
            uri = URI.create(databaseUrl);
        } catch (IllegalArgumentException ex) {
            return props;
        }
        if (uri.getHost() == null) {
            return props;
        }
        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://")
                .append(uri.getHost());
        if (uri.getPort() != -1) {
            jdbc.append(':').append(uri.getPort());
        }
        String path = uri.getPath();
        jdbc.append((path == null || path.isEmpty()) ? "/" : path);
        if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
            jdbc.append('?').append(uri.getQuery());
        }
        props.put("spring.datasource.url", jdbc.toString());

        String userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
            int sep = userInfo.indexOf(':');
            String username = sep >= 0 ? userInfo.substring(0, sep) : userInfo;
            String password = sep >= 0 ? userInfo.substring(sep + 1) : "";
            if (!username.isBlank()) {
                props.put("spring.datasource.username",
                        URLDecoder.decode(username, StandardCharsets.UTF_8));
            }
            if (!password.isBlank()) {
                props.put("spring.datasource.password",
                        URLDecoder.decode(password, StandardCharsets.UTF_8));
            }
        }
        return props;
    }
}
