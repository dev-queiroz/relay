package dev.queiroz.relay.operations;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test void propagatesValidRequestIdAndDoesNotPlaceSensitiveHeadersInMdc() throws Exception {
        String requestId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, requestId);
        request.addHeader("X-Relay-Api-Key", "secret-api-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> inRequest = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> inRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertEquals(requestId, inRequest.get());
        assertEquals(requestId, response.getHeader(CorrelationIdFilter.HEADER));
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
        assertNull(MDC.get("apiKey"));
        assertNull(MDC.get("payload"));
    }

    @Test void replacesUntrustedRequestIdWithGeneratedUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "not-a-valid-correlation-id\r\nsecret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertDoesNotThrow(() -> UUID.fromString(response.getHeader(CorrelationIdFilter.HEADER)));
    }
}
