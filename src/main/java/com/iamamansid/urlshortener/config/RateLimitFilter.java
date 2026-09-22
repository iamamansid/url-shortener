package com.iamamansid.urlshortener.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iamamansid.urlshortener.config.AppProperties;
import com.iamamansid.urlshortener.dto.ErrorResponse;
import com.iamamansid.urlshortener.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Token-bucket rate limiting on short-URL creation: {@code app.rate-limit-per-minute}
 * requests per minute per client IP. Rejected requests get a JSON 429 with a
 * {@code Retry-After} header. Exceptions are written directly here because
 * filters run outside {@code @RestControllerAdvice}.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String CREATE_PATH = "/api/v1/urls";

    private final RateLimiterService rateLimiter;
    private final AppProperties props;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiterService rateLimiter, AppProperties props, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isCreateRequest(request) && !rateLimiter.tryConsume(clientIp(request))) {
            writeRateLimited(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isCreateRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && CREATE_PATH.equals(request.getServletPath());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeRateLimited(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Rate limit exceeded: max " + props.rateLimitPerMinute() + " requests per minute",
                request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
