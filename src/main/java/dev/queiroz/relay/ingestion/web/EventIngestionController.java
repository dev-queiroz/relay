package dev.queiroz.relay.ingestion.web;

import dev.queiroz.relay.ingestion.application.AcceptEventCommand;
import dev.queiroz.relay.ingestion.application.AcceptedEvent;
import dev.queiroz.relay.ingestion.application.EventAcceptanceService;
import dev.queiroz.relay.ingestion.domain.EventPayload;
import dev.queiroz.relay.ingestion.domain.EventType;
import dev.queiroz.relay.ingestion.domain.TenantId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
public class EventIngestionController {

    static final int MAX_REQUEST_BYTES = 64 * 1024;
    private static final String NORMALIZED_CONTENT_TYPE = "application/json";
    private static final Pattern UUID_FORMAT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );

    private final EventAcceptanceService eventAcceptanceService;
    private final ObjectMapper objectMapper;
    private final TenantId developmentTenant;

    public EventIngestionController(
            EventAcceptanceService eventAcceptanceService,
            ObjectMapper objectMapper,
            @Value("${relay.ingestion.development-tenant-id}") String developmentTenantId
    ) {
        this.eventAcceptanceService = eventAcceptanceService;
        this.objectMapper = objectMapper;
        this.developmentTenant = new TenantId(developmentTenantId);
    }

    @PostMapping("/v1/events")
    public ResponseEntity<AcceptedEventResponse> accept(
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) byte[] body
    ) {
        requireJsonContentType(contentType);
        validateIdempotencyKey(idempotencyKey);
        if (body == null) {
            throw new InvalidRequestException("request body is required");
        }
        if (body.length > MAX_REQUEST_BYTES) {
            throw new PayloadTooLargeException();
        }

        JsonNode request = parseObject(body);
        JsonNode type = request.get("type");
        if (type == null || !type.isTextual()) {
            throw new InvalidRequestException("type is required and must be a string");
        }
        JsonNode payload = request.get("payload");
        if (payload == null) {
            throw new InvalidRequestException("payload is required");
        }

        AcceptedEvent accepted = eventAcceptanceService.accept(new AcceptEventCommand(
                developmentTenant,
                idempotencyKey,
                fingerprint(body),
                new EventType(type.textValue()),
                EventPayload.from(payload)
        ));
        String eventId = accepted.eventId().value().toString();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/v1/events/" + eventId))
                .body(new AcceptedEventResponse(eventId, "accepted"));
    }

    private void requireJsonContentType(String contentType) {
        if (contentType == null) {
            throw new UnsupportedContentTypeException();
        }
        try {
            if (!MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType))) {
                throw new UnsupportedContentTypeException();
            }
        } catch (IllegalArgumentException exception) {
            throw new UnsupportedContentTypeException();
        }
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.length() > 64 || !UUID_FORMAT.matcher(key).matches()) {
            throw new InvalidRequestException("Idempotency-Key must be a UUID");
        }
        try {
            UUID.fromString(key);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("Idempotency-Key must be a UUID");
        }
    }

    private JsonNode parseObject(byte[] body) {
        try {
            JsonNode request = objectMapper.readTree(body);
            if (request == null || !request.isObject()) {
                throw new InvalidRequestException("request body must be a JSON object");
            }
            return request;
        } catch (InvalidRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidRequestException("request body must be valid JSON");
        }
    }

    private String fingerprint(byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(NORMALIZED_CONTENT_TYPE.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            return HexFormat.of().formatHex(digest.digest(body));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record AcceptedEventResponse(String eventId, String status) {
    }
}
