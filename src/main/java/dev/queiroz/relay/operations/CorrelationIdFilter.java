package dev.queiroz.relay.operations;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-Id";
    public static final String LEGACY_HEADER = "X-Relay-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        if (!validUuid(supplied)) supplied = request.getHeader(LEGACY_HEADER);
        String requestId = validUuid(supplied) ? supplied : UUID.randomUUID().toString();
        response.setHeader(HEADER, requestId);
        response.setHeader(LEGACY_HEADER, requestId);
        try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_KEY, requestId)) {
            chain.doFilter(request, response);
        }
    }

    private boolean validUuid(String value) {
        if (value == null) return false;
        try { UUID.fromString(value); return true; } catch (IllegalArgumentException exception) { return false; }
    }
}
