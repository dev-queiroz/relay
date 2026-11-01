package dev.queiroz.relay.delivery.web;

import dev.queiroz.relay.api.pagination.CursorCodec;
import dev.queiroz.relay.api.pagination.InvalidCursorException;
import dev.queiroz.relay.delivery.application.EndpointAdministrationService;
import dev.queiroz.relay.delivery.domain.Endpoint;
import dev.queiroz.relay.ingestion.application.ApiKeyService;
import dev.queiroz.relay.ingestion.application.TenantRateLimitService;
import dev.queiroz.relay.ingestion.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EndpointController.class)
class EndpointControllerTest {
    private static final TenantId TENANT = new TenantId("tenant-a");
    private static final String API_KEY = "tenant-a-key";
    @Autowired MockMvc mockMvc;
    @MockitoBean EndpointAdministrationService endpoints;
    @MockitoBean ApiKeyService apiKeys;
    @MockitoBean TenantRateLimitService limits;
    @MockitoBean CursorCodec cursors;

    @BeforeEach
    void authenticate() {
        given(apiKeys.authenticate(API_KEY)).willReturn(Optional.of(TENANT));
        given(limits.allowRequest(TENANT)).willReturn(true);
    }

    @Test
    void createsEndpointAndReturnsSigningSecretOnlyInNoStoreResponse() throws Exception {
        Endpoint endpoint = endpoint("endpoint");
        given(endpoints.register(any(), any(), any())).willReturn(new EndpointAdministrationService.RegisteredEndpoint(endpoint, "one-time-secret"));

        mockMvc.perform(post("/v1/endpoints").header("X-Relay-Api-Key", API_KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" billing \",\"destinationUrl\":\"https://public.example/hooks\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/endpoints/" + endpoint.id()))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.id").value(endpoint.id().toString()))
                .andExpect(jsonPath("$.signingSecret").value("one-time-secret"))
                .andExpect(jsonPath("$.tenantId").doesNotExist());

        ArgumentCaptor<TenantId> tenant = ArgumentCaptor.forClass(TenantId.class);
        verify(endpoints).register(tenant.capture(), org.mockito.ArgumentMatchers.eq(" billing "), org.mockito.ArgumentMatchers.eq("https://public.example/hooks"));
        assertEquals(TENANT, tenant.getValue());
    }

    @Test
    void readsMetadataWithoutSecretsAndHidesOtherTenantEndpoints() throws Exception {
        Endpoint endpoint = endpoint("endpoint");
        given(endpoints.find(TENANT, endpoint.id())).willReturn(Optional.of(endpoint));
        TenantId otherTenant = new TenantId("tenant-b");
        given(apiKeys.authenticate("tenant-b-key")).willReturn(Optional.of(otherTenant));
        given(limits.allowRequest(otherTenant)).willReturn(true);
        given(endpoints.find(otherTenant, endpoint.id())).willReturn(Optional.empty());
        UUID absent = UUID.randomUUID();
        given(endpoints.find(TENANT, absent)).willReturn(Optional.empty());

        mockMvc.perform(get("/v1/endpoints/{id}", endpoint.id()).header("X-Relay-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinationUrl").value("https://public.example/hooks"))
                .andExpect(jsonPath("$.signingSecret").doesNotExist())
                .andExpect(jsonPath("$.tenantId").doesNotExist());
        mockMvc.perform(get("/v1/endpoints/{id}", absent).header("X-Relay-Api-Key", API_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("The endpoint was not found"));
        mockMvc.perform(get("/v1/endpoints/{id}", endpoint.id()).header("X-Relay-Api-Key", "tenant-b-key"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("The endpoint was not found"));
    }

    @Test
    void validatesPatchAndReturnsNotFoundWithoutTenantOracle() throws Exception {
        UUID id = UUID.randomUUID();
        given(endpoints.update(any(), any(), any(), any(), any())).willReturn(Optional.empty());

        mockMvc.perform(patch("/v1/endpoints/{id}", id).header("X-Relay-Api-Key", API_KEY).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/v1/endpoints/{id}", id).header("X-Relay-Api-Key", API_KEY).contentType(MediaType.APPLICATION_JSON).content("{\"tenantId\":\"other\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/v1/endpoints/{id}", id).header("X-Relay-Api-Key", API_KEY).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("The endpoint was not found"));
    }

    @Test
    void paginatesWithinAuthenticatedTenantAndRejectsInvalidQueries() throws Exception {
        Endpoint newest = endpoint("newest");
        given(endpoints.list(any(), any(), anyInt())).willReturn(List.of(newest));

        mockMvc.perform(get("/v1/endpoints").header("X-Relay-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(newest.id().toString()))
                .andExpect(jsonPath("$.data[0].signingSecret").doesNotExist())
                .andExpect(jsonPath("$.page.hasMore").value(false))
                .andExpect(jsonPath("$.page.nextCursor").value(org.hamcrest.Matchers.nullValue()));
        verify(endpoints).list(org.mockito.ArgumentMatchers.eq(TENANT), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(21));
        mockMvc.perform(get("/v1/endpoints?limit=0").header("X-Relay-Api-Key", API_KEY)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/endpoints?limit=101").header("X-Relay-Api-Key", API_KEY)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/endpoints?limit=100").header("X-Relay-Api-Key", API_KEY)).andExpect(status().isOk());
        mockMvc.perform(get("/v1/endpoints?unknown=value").header("X-Relay-Api-Key", API_KEY)).andExpect(status().isBadRequest());
        given(cursors.decode("tampered", "v1/endpoints", TENANT)).willThrow(new InvalidCursorException());
        mockMvc.perform(get("/v1/endpoints?cursor=tampered").header("X-Relay-Api-Key", API_KEY)).andExpect(status().isBadRequest());
    }

    @Test
    void rotatesSecretOnceAndUsesStandardAuthenticationAndRateLimits() throws Exception {
        UUID id = UUID.randomUUID();
        given(endpoints.rotateSigningSecret(TENANT, id)).willReturn(Optional.of("rotated-secret"));
        mockMvc.perform(post("/v1/endpoints/{id}/signing-secret/rotate", id).header("X-Relay-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.signingSecret").value("rotated-secret"));

        mockMvc.perform(post("/v1/endpoints").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        given(limits.allowRequest(TENANT)).willReturn(false);
        mockMvc.perform(post("/v1/endpoints").header("X-Relay-Api-Key", API_KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"billing\",\"destinationUrl\":\"https://public.example/hooks\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    void returnsSafeBadRequestAndStorageUnavailableResponses() throws Exception {
        mockMvc.perform(get("/v1/endpoints/not-a-uuid").header("X-Relay-Api-Key", API_KEY))
                .andExpect(status().isBadRequest());
        given(endpoints.register(any(), any(), any())).willThrow(new DataAccessResourceFailureException("database password"));
        mockMvc.perform(post("/v1/endpoints").header("X-Relay-Api-Key", API_KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"billing\",\"destinationUrl\":\"https://public.example/hooks\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Event storage is temporarily unavailable; retry the same request"));
    }

    @Test
    void authenticatesBeforeReadingAndBoundsMutationBodies() throws Exception {
        String oversized = "{\"name\":\"" + "a".repeat(EndpointController.MAX_REQUEST_BYTES) + "\"}";
        mockMvc.perform(post("/v1/endpoints").contentType(MediaType.APPLICATION_JSON).content(oversized))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/endpoints").header("X-Relay-Api-Key", API_KEY).contentType(MediaType.APPLICATION_JSON).content(oversized))
                .andExpect(status().isPayloadTooLarge());
    }

    private Endpoint endpoint(String name) {
        return new Endpoint(UUID.randomUUID(), TENANT, name, URI.create("https://public.example/hooks"), true, Instant.parse("2026-10-31T12:00:00Z"));
    }
}
