package dev.queiroz.relay.ingestion.web;

import dev.queiroz.relay.ingestion.application.AcceptEventCommand;
import dev.queiroz.relay.ingestion.application.AcceptedEvent;
import dev.queiroz.relay.ingestion.application.EventAcceptanceService;
import dev.queiroz.relay.ingestion.application.ApiKeyService;
import dev.queiroz.relay.ingestion.domain.EventPayload;
import dev.queiroz.relay.ingestion.domain.EventType;
import dev.queiroz.relay.ingestion.domain.TenantId;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
    private final ApiKeyService apiKeys;

    public EventIngestionController(
            EventAcceptanceService eventAcceptanceService,
            ObjectMapper objectMapper, ApiKeyService apiKeys
    ) {
        this.eventAcceptanceService = eventAcceptanceService;
        this.objectMapper = objectMapper;
        this.apiKeys = apiKeys;
    }

    @PostMapping("/v1/events")
    public ResponseEntity<AcceptedEventResponse> accept(
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Relay-Api-Key", required = false) String apiKey,
            HttpServletRequest request
    ) throws IOException {
        requireJsonContentType(contentType);
        validateIdempotencyKey(idempotencyKey);
        TenantId tenant = apiKeys.authenticate(apiKey).orElseThrow(UnauthorizedException::new);
        byte[] body = readLimitedBody(request);

        JsonNode eventRequest = parseObject(body);
        JsonNode type = eventRequest.get("type");
        if (type == null || !type.isTextual()) {
            throw new InvalidRequestException("type is required and must be a string");
        }
        JsonNode payload = eventRequest.get("payload");
        if (payload == null) {
            throw new InvalidRequestException("payload is required");
        }

        AcceptedEvent accepted = eventAcceptanceService.accept(new AcceptEventCommand(
                tenant,
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

    private byte[] readLimitedBody(HttpServletRequest request) throws IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > MAX_REQUEST_BYTES) {
            throw new PayloadTooLargeException();
        }

        ByteArrayOutputStream body = new ByteArrayOutputStream(
                contentLength >= 0 ? (int) contentLength : 0
        );
        byte[] buffer = new byte[8192];
        InputStream input = request.getInputStream();
        while (true) {
            int bytesRead = input.read(buffer, 0, Math.min(buffer.length, MAX_REQUEST_BYTES + 1 - body.size()));
            if (bytesRead == -1) {
                break;
            }
            body.write(buffer, 0, bytesRead);
            if (body.size() > MAX_REQUEST_BYTES) {
                throw new PayloadTooLargeException();
            }
        }
        return body.toByteArray();
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
