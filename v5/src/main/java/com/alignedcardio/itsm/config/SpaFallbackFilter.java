package com.alignedcardio.itsm.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Forward any non-API, non-asset, non-static request to /index.html so React Router
 * can handle deep-link client-side routes on browser refresh.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SpaFallbackFilter extends OncePerRequestFilter {

    private static final Set<String> EXACT_SKIP = Set.of("/", "/index.html");

    private static final Set<String> PREFIX_SKIP = Set.of(
            "/api/",
            "/actuator/",
            "/swagger-ui/",
            "/v3/api-docs/",
            "/ws",
            "/ws/",
            "/error",
            "/oauth2/",
            "/callback/"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();

        if (shouldPassThrough(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        request.getRequestDispatcher("/index.html").forward(request, response);
    }

    private boolean shouldPassThrough(String uri) {
        if (EXACT_SKIP.contains(uri)) {
            return true;
        }
        for (String prefix : PREFIX_SKIP) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        int lastSlash = uri.lastIndexOf('/');
        int lastDot = uri.lastIndexOf('.');
        // Skip files with extensions (assets, .js, .css, .png, etc.)
        return lastDot > lastSlash;
    }
}
