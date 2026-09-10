package com.alignedcardio.itsm.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockRequestDispatcher;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SpaFallbackFilterTest {

    private final SpaFallbackFilter filter = new SpaFallbackFilter();

    @Test
    void forwardsLoginWithQueryStringToIndexHtml() throws ServletException, IOException {
        RecordingMockHttpServletRequest request = new RecordingMockHttpServletRequest();
        request.setRequestURI("/login");
        request.setQueryString("timeout=1");
        request.setParameter("timeout", "1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest(), "/login should not be passed through to the resource handler");
        assertEquals("/index.html", request.forwardPath);
    }

    @Test
    void forwardsLogoutToIndexHtml() throws ServletException, IOException {
        RecordingMockHttpServletRequest request = new RecordingMockHttpServletRequest();
        request.setRequestURI("/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest(), "/logout should not be passed through to the resource handler");
        assertEquals("/index.html", request.forwardPath);
    }

    @Test
    void passesApiPathThrough() throws ServletException, IOException {
        RecordingMockHttpServletRequest request = new RecordingMockHttpServletRequest();
        request.setRequestURI("/api/v1/incidents");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest(), "/api paths should be passed through");
    }

    private static class RecordingMockHttpServletRequest extends MockHttpServletRequest {
        String forwardPath;

        @Override
        public RequestDispatcher getRequestDispatcher(String path) {
            this.forwardPath = path;
            return new MockRequestDispatcher(path);
        }
    }
}
