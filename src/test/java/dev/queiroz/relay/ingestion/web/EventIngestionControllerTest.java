package dev.queiroz.relay.ingestion.web;

import dev.queiroz.relay.ingestion.application.AcceptedEvent;
import dev.queiroz.relay.ingestion.application.AcceptEventCommand;
import dev.queiroz.relay.ingestion.application.EventAcceptanceService;
import dev.queiroz.relay.ingestion.application.IdempotencyConflictException;
import dev.queiroz.relay.ingestion.domain.EventId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EventIngestionController.class, properties = "relay.ingestion.development-tenant-id=local-test-tenant")
class EventIngestionControllerTest {

    private static final String KEY = "b4af55af-c4fe-4b06-8bf6-8d80ae3cb8c1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventAcceptanceService eventAcceptanceService;

    @Test
    void acceptsNewEventAndReturnsStableResponseContract() throws Exception {
        UUID eventId = UUID.randomUUID();
        given(eventAcceptanceService.accept(any())).willReturn(new AcceptedEvent(new EventId(eventId)));

        mockMvc.perform(validRequest("{\"type\":\"payment.confirmed\",\"payload\":{\"paymentId\":\"pay_123\"}}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/v1/events/" + eventId))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void usesConfiguredDevelopmentTenantInsteadOfRequestTenantHeaders() throws Exception {
        given(eventAcceptanceService.accept(any())).willReturn(new AcceptedEvent(new EventId(UUID.randomUUID())));

        mockMvc.perform(validRequest("{\"type\":\"payment.confirmed\",\"payload\":{}}")
                        .header("X-Tenant-Id", "spoofed-tenant"))
                .andExpect(status().isAccepted());

        org.mockito.ArgumentCaptor<AcceptEventCommand> command = org.mockito.ArgumentCaptor.forClass(AcceptEventCommand.class);
        verify(eventAcceptanceService).accept(command.capture());
        org.junit.jupiter.api.Assertions.assertEquals("local-test-tenant", command.getValue().tenantId().value());
    }

    @Test
    void rejectsMalformedRequestWithSafeProblemDetails() throws Exception {
        mockMvc.perform(validRequest("{\"type\":\"payment.confirmed\""))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("request body must be valid JSON"));
    }

    @Test
    void rejectsReusedKeyWithDifferentRequest() throws Exception {
        given(eventAcceptanceService.accept(any())).willThrow(new IdempotencyConflictException());

        mockMvc.perform(validRequest("{\"type\":\"payment.confirmed\",\"payload\":{}}"))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Idempotency-Key was previously used with a different request"));
    }

    @Test
    void reportsDatabaseOutageWithoutExposingDatabaseDetails() throws Exception {
        given(eventAcceptanceService.accept(any())).willThrow(new DataAccessResourceFailureException("database password leaked"));

        mockMvc.perform(validRequest("{\"type\":\"payment.confirmed\",\"payload\":{}}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value("Event storage is temporarily unavailable; retry the same request"));
    }

    @Test
    void rejectsUnsupportedContentTypeAndOversizedRequests() throws Exception {
        mockMvc.perform(post("/v1/events")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));

        mockMvc.perform(validRequest("{\"type\":\"a\",\"payload\":{\"data\":\"" + "a".repeat(EventIngestionController.MAX_REQUEST_BYTES) + "\"}}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413));
    }

    @Test
    void rejectsMissingOrMalformedIdempotencyKey() throws Exception {
        mockMvc.perform(post("/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validRequest(String body) {
        return post("/v1/events")
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
