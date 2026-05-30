package com.fsd.bootstrap.config;

import com.fsd.admin.metrics.ApiRequestMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiRequestTimingFilter extends OncePerRequestFilter {

    private final ApiRequestMetrics apiRequestMetrics;

    public ApiRequestTimingFilter(ApiRequestMetrics apiRequestMetrics) {
        this.apiRequestMetrics = apiRequestMetrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            String path = request.getRequestURI();
            if (path != null && path.startsWith("/api/")) {
                apiRequestMetrics.record(System.currentTimeMillis() - start);
            }
        }
    }
}
