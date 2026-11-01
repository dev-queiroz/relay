package dev.queiroz.relay.delivery.web;

import dev.queiroz.relay.api.pagination.CursorCodec;
import dev.queiroz.relay.api.pagination.KeysetCursor;
import dev.queiroz.relay.delivery.application.EndpointAdministrationService;
import dev.queiroz.relay.delivery.domain.Endpoint;
import dev.queiroz.relay.ingestion.application.ApiKeyService;
import dev.queiroz.relay.ingestion.application.RateLimitExceededException;
import dev.queiroz.relay.ingestion.application.TenantRateLimitService;
import dev.queiroz.relay.ingestion.domain.TenantId;
import dev.queiroz.relay.ingestion.web.UnauthorizedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Public, tenant-scoped endpoint administration API. Plaintext secrets never enter logs or storage responses. */
@RestController
@RequestMapping("/v1/endpoints")
public class EndpointController {
    private static final String ROUTE = "v1/endpoints";
    static final int MAX_REQUEST_BYTES = 64 * 1024;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private final EndpointAdministrationService endpoints;
    private final ApiKeyService apiKeys;
    private final TenantRateLimitService limits;
    private final CursorCodec cursors;
    private final ObjectMapper objectMapper;

    public EndpointController(EndpointAdministrationService endpoints, ApiKeyService apiKeys,
                              TenantRateLimitService limits, CursorCodec cursors, ObjectMapper objectMapper) {
        this.endpoints = endpoints;
        this.apiKeys = apiKeys;
        this.limits = limits;
        this.cursors = cursors;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<CreatedEndpointResponse> create(@RequestHeader(value = "X-Relay-Api-Key", required = false) String apiKey,
                                                           HttpServletRequest httpRequest) throws IOException {
        TenantId tenant = authenticatedTenant(apiKey);
        CreateEndpointRequest request = createRequest(readJsonObject(httpRequest));
        EndpointAdministrationService.RegisteredEndpoint registered;
        try {
            registered = endpoints.register(tenant, request.name(), request.destinationUrl());
        } catch (IllegalArgumentException exception) {
            throw new EndpointInvalidRequestException();
        }
        Endpoint endpoint = registered.endpoint();
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/v1/endpoints/" + endpoint.id()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new CreatedEndpointResponse(endpoint, registered.signingSecret()));
    }

    @GetMapping
    public EndpointPageResponse list(@RequestHeader(value = "X-Relay-Api-Key", required = false) String apiKey,
                                     @RequestParam Map<String, String> query) {
        TenantId tenant = authenticatedTenant(apiKey);
        PageRequest page = pageRequest(query);
        KeysetCursor cursor = page.cursor() == null ? null : cursors.decode(page.cursor(), ROUTE, tenant);
        List<Endpoint> results = endpoints.list(tenant, cursor, page.limit() + 1);
        boolean hasMore = results.size() > page.limit();
        List<Endpoint> data = hasMore ? results.subList(0, page.limit()) : results;
        String nextCursor = hasMore ? cursors.encode(ROUTE, tenant, cursorFor(data.getLast())) : null;
        return new EndpointPageResponse(data.stream().map(EndpointResponse::new).toList(), new PageResponse(hasMore, nextCursor));
    }

    @GetMapping("/{endpointId}")
    public EndpointResponse get(@RequestHeader(value = "X-Relay-Api-Key", required = false) String apiKey,
                                @PathVariable UUID endpointId) {
        TenantId tenant = authenticatedTenant(apiKey);
        return endpoints.find(tenant, endpointId).map(EndpointResponse::new).orElseThrow(EndpointNotFoundException::new);
    }

    @PatchMapping("/{endpointId}")
    public EndpointResponse update(@RequestHeader(value = "X-Relay-Api-Key", required = false) String apiKey,
                                   @PathVariable UUID endpointId, HttpServletRequest httpRequest) throws IOException {
        TenantId tenant = authenticatedTenant(apiKey);
        UpdateEndpointRequest request = updateRequest(readJsonObject(httpRequest));
        try {
            return endpoints.update(tenant, endpointId, request.name(), request.destinationUrl(), request.enabled())
                    .map(EndpointResponse::new).orElseThrow(EndpointNotFoundException::new);
        } catch (IllegalArgumentException exception) {
            throw new EndpointInvalidRequestException();
        }
    }

    @PostMapping("/{endpointId}/signing-secret/rotate")
    public ResponseEntity<SigningSecretResponse> rotate(@RequestHeader(value = "X-Relay-Api-Key", required = false) String apiKey,
                                                         @PathVariable UUID endpointId) {
        TenantId tenant = authenticatedTenant(apiKey);
        String secret = endpoints.rotateSigningSecret(tenant, endpointId).orElseThrow(EndpointNotFoundException::new);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(new SigningSecretResponse(secret));
    }

    private TenantId authenticatedTenant(String apiKey) {
        TenantId tenant = apiKeys.authenticate(apiKey).orElseThrow(UnauthorizedException::new);
        if (!limits.allowRequest(tenant)) throw new RateLimitExceededException();
        return tenant;
    }

    private JsonNode readJsonObject(HttpServletRequest request) throws IOException {
        String contentType = request.getContentType();
        try {
            if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType))) {
                throw new EndpointUnsupportedContentTypeException();
            }
        } catch (IllegalArgumentException exception) {
            throw new EndpointUnsupportedContentTypeException();
        }
        long contentLength = request.getContentLengthLong();
        if (contentLength > MAX_REQUEST_BYTES) throw new EndpointPayloadTooLargeException();
        ByteArrayOutputStream body = new ByteArrayOutputStream(contentLength >= 0 ? (int) contentLength : 0);
        byte[] buffer = new byte[8192];
        InputStream input = request.getInputStream();
        while (true) {
            int read = input.read(buffer, 0, Math.min(buffer.length, MAX_REQUEST_BYTES + 1 - body.size()));
            if (read == -1) break;
            body.write(buffer, 0, read);
            if (body.size() > MAX_REQUEST_BYTES) throw new EndpointPayloadTooLargeException();
        }
        try {
            JsonNode parsed = objectMapper.readTree(body.toByteArray());
            if (parsed == null || !parsed.isObject()) throw new EndpointInvalidRequestException();
            return parsed;
        } catch (EndpointInvalidRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new EndpointInvalidRequestException();
        }
    }

    private CreateEndpointRequest createRequest(JsonNode body) {
        requireObjectWithOnly(body, Set.of("name", "destinationUrl"));
        String name = requiredText(body, "name");
        String destinationUrl = requiredText(body, "destinationUrl");
        return new CreateEndpointRequest(name, destinationUrl);
    }

    private UpdateEndpointRequest updateRequest(JsonNode body) {
        requireObjectWithOnly(body, Set.of("name", "destinationUrl", "enabled"));
        if (body.size() == 0) throw new EndpointInvalidRequestException();
        String name = optionalText(body, "name");
        String destinationUrl = optionalText(body, "destinationUrl");
        Boolean enabled = optionalBoolean(body, "enabled");
        if (name == null && destinationUrl == null && enabled == null) throw new EndpointInvalidRequestException();
        return new UpdateEndpointRequest(name, destinationUrl, enabled);
    }

    private void requireObjectWithOnly(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject()) throw new EndpointInvalidRequestException();
        if (body.properties().stream().map(Map.Entry::getKey).anyMatch(field -> !allowed.contains(field))) {
            throw new EndpointInvalidRequestException();
        }
    }

    private String requiredText(JsonNode body, String field) {
        String value = optionalText(body, field);
        if (value == null) throw new EndpointInvalidRequestException();
        return value;
    }

    private String optionalText(JsonNode body, String field) {
        if (!body.has(field)) return null;
        JsonNode value = body.get(field);
        if (value == null || value.isNull() || !value.isTextual()) throw new EndpointInvalidRequestException();
        return value.textValue();
    }

    private Boolean optionalBoolean(JsonNode body, String field) {
        if (!body.has(field)) return null;
        JsonNode value = body.get(field);
        if (value == null || value.isNull() || !value.isBoolean()) throw new EndpointInvalidRequestException();
        return value.booleanValue();
    }

    private PageRequest pageRequest(Map<String, String> query) {
        if (!Set.of("limit", "cursor").containsAll(query.keySet())) throw new EndpointInvalidRequestException();
        int limit = DEFAULT_LIMIT;
        if (query.containsKey("limit")) {
            try { limit = Integer.parseInt(query.get("limit")); }
            catch (RuntimeException exception) { throw new EndpointInvalidRequestException(); }
            if (limit < 1 || limit > MAX_LIMIT) throw new EndpointInvalidRequestException();
        }
        String cursor = query.get("cursor");
        if (cursor != null && cursor.isBlank()) throw new EndpointInvalidRequestException();
        return new PageRequest(limit, cursor);
    }

    private KeysetCursor cursorFor(Endpoint endpoint) { return new KeysetCursor(endpoint.createdAt(), endpoint.id()); }

    private record CreateEndpointRequest(String name, String destinationUrl) { }
    private record UpdateEndpointRequest(String name, String destinationUrl, Boolean enabled) { }
    private record PageRequest(int limit, String cursor) { }
    public record EndpointResponse(UUID id, String name, String destinationUrl, boolean enabled, Instant createdAt) {
        EndpointResponse(Endpoint endpoint) { this(endpoint.id(), endpoint.name(), endpoint.destinationUrl().toString(), endpoint.enabled(), endpoint.createdAt()); }
    }
    public record CreatedEndpointResponse(UUID id, String name, String destinationUrl, boolean enabled, Instant createdAt, String signingSecret) {
        CreatedEndpointResponse(Endpoint endpoint, String signingSecret) { this(endpoint.id(), endpoint.name(), endpoint.destinationUrl().toString(), endpoint.enabled(), endpoint.createdAt(), signingSecret); }
    }
    public record SigningSecretResponse(String signingSecret) { }
    public record PageResponse(boolean hasMore, String nextCursor) { }
    public record EndpointPageResponse(List<EndpointResponse> data, PageResponse page) { }
}
