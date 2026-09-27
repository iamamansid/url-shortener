package com.iamamansid.urlshortener.filter;

import com.iamamansid.urlshortener.service.PageViewService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Counts homepage visits for the admin "site visitors" chart. Only the
 * landing page ({@code /} and {@code /index.html}) is tracked — the redirect
 * path is deliberately untouched so click analytics stay the single source
 * for link traffic.
 */
@Component
public class PageViewFilter extends OncePerRequestFilter {

    private final PageViewService pageViewService;

    public PageViewFilter(PageViewService pageViewService) {
        this.pageViewService = pageViewService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);
        try {
            if ("GET".equalsIgnoreCase(request.getMethod())) {
                String uri = request.getRequestURI();
                if ("/".equals(uri) || "/index.html".equals(uri)) {
                    pageViewService.record("/");
                }
            }
        } catch (Exception ignored) {
            // Tracking must never break the request.
        }
    }
}
