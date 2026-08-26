package com.hengpick.mall.observability;

import com.hengpick.mall.shared.UlidGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final Pattern TRACEPARENT =
            Pattern.compile("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");

    private final UlidGenerator ulidGenerator;
    private final SecureRandom random = new SecureRandom();

    RequestCorrelationFilter(UlidGenerator ulidGenerator) {
        this.ulidGenerator = ulidGenerator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var requestId = acceptedRequestId(request.getHeader("X-Request-Id"));
        var traceparent = acceptedTraceparent(request.getHeader("traceparent"));
        var startedAt = System.nanoTime();
        MDC.put("requestId", requestId);
        MDC.put("traceparent", traceparent);
        response.setHeader("X-Request-Id", requestId);
        response.setHeader("traceparent", traceparent);
        try {
            filterChain.doFilter(request, response);
        } finally {
            var durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info(
                    "request_completed method={} path={} status={} durationMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs);
            MDC.clear();
        }
    }

    private String acceptedRequestId(String candidate) {
        return candidate != null && REQUEST_ID.matcher(candidate).matches() ? candidate : ulidGenerator.next();
    }

    private String acceptedTraceparent(String candidate) {
        if (candidate != null && TRACEPARENT.matcher(candidate).matches()) {
            return candidate;
        }
        var traceId = new byte[16];
        var parentId = new byte[8];
        random.nextBytes(traceId);
        random.nextBytes(parentId);
        return "00-" + HexFormat.of().formatHex(traceId) + "-" + HexFormat.of().formatHex(parentId) + "-01";
    }
}
