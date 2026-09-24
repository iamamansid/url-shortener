package com.iamamansid.urlshortener.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Titles and descriptions for the interactive API docs (Swagger UI).
 * Written in plain language so non-technical visitors can follow along.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI urlShortenerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("URL Shortener API")
                .version("1.0.0")
                .description("Turn long links into short, shareable codes. "
                        + "Create a short link below, open it in a browser, "
                        + "and check how many times it was clicked."));
    }
}
