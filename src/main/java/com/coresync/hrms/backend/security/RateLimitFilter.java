package com.coresync.hrms.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, RequestInfo> requestCounts = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final long TIME_WINDOW_MS = 60000;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String clientIp = request.getRemoteAddr();
        long currentTime = System.currentTimeMillis();

        requestCounts.compute(clientIp, (key, requestInfo) -> {
            if (requestInfo == null || currentTime - requestInfo.startTime > TIME_WINDOW_MS) {
                return new RequestInfo(currentTime, new AtomicInteger(1));
            }
            requestInfo.count.incrementAndGet();
            return requestInfo;
        });

        RequestInfo info = requestCounts.get(clientIp);
        if (info.count.get() > MAX_REQUESTS_PER_MINUTE) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Too many requests. Please try again later.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static class RequestInfo {
        long startTime;
        AtomicInteger count;

        RequestInfo(long startTime, AtomicInteger count) {
            this.startTime = startTime;
            this.count = count;
        }
    }
}
