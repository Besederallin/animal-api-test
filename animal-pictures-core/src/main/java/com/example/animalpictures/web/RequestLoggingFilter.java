package com.example.animalpictures.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request an ID and logs one line per request with its status and duration.
 * <ul>
 *   <li>The ID is taken from the {@code X-Request-Id} header if the caller sent one, otherwise generated.</li>
 *   <li>It is returned in the {@code X-Request-Id} response header, so a client can report it.</li>
 *   <li>It is put in the logging MDC as {@code requestId}, so <em>every</em> log line written while handling
 *       the request (service, HTTP client, errors) carries it. Search the logs for the ID to see the whole story.</li>
 * </ul>
 * Actuator calls (health checks, Prometheus scrapes) are logged at DEBUG only, to keep the log readable.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    /** Accept only short, safe IDs from callers so they cannot inject text into the logs. */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || !SAFE_ID.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            String query = request.getQueryString() == null ? "" : "?" + request.getQueryString();
            if (request.getRequestURI().startsWith("/actuator")) {
                log.debug("{} {}{} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), query,
                        response.getStatus(), durationMs);
            } else {
                log.info("{} {}{} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), query,
                        response.getStatus(), durationMs);
            }
            MDC.remove(MDC_KEY);
        }
    }
}
