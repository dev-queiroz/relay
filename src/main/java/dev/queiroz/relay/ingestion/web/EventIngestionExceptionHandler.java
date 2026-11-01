package dev.queiroz.relay.ingestion.web;

import dev.queiroz.relay.ingestion.application.IdempotencyConflictException;
import dev.queiroz.relay.ingestion.application.RateLimitExceededException;
import dev.queiroz.relay.api.pagination.InvalidCursorException;
import dev.queiroz.relay.delivery.web.EndpointInvalidRequestException;
import dev.queiroz.relay.delivery.web.EndpointNotFoundException;
import dev.queiroz.relay.delivery.web.EndpointPayloadTooLargeException;
import dev.queiroz.relay.delivery.web.EndpointUnsupportedContentTypeException;
import dev.queiroz.relay.status.web.StatusNotFoundException;
import dev.queiroz.relay.status.web.StatusRequestException;
import dev.queiroz.relay.ingestion.domain.InvalidEventException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

@RestControllerAdvice
public class EventIngestionExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({InvalidRequestException.class, InvalidEventException.class})
    ProblemDetail invalidRequest(RuntimeException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid event request", exception.getMessage());
    }

    @ExceptionHandler({EndpointInvalidRequestException.class, InvalidCursorException.class})
    ProblemDetail invalidEndpointRequest() {
        return problem(HttpStatus.BAD_REQUEST, "Invalid endpoint request", "The endpoint request is invalid");
    }

    @ExceptionHandler(EndpointNotFoundException.class)
    ProblemDetail endpointNotFound() {
        return problem(HttpStatus.NOT_FOUND, "Not found", "The endpoint was not found");
    }
    @ExceptionHandler(StatusNotFoundException.class) ProblemDetail statusNotFound() { return problem(HttpStatus.NOT_FOUND, "Not found", "The resource was not found"); }
    @ExceptionHandler(StatusRequestException.class) ProblemDetail statusInvalidRequest() { return problem(HttpStatus.BAD_REQUEST, "Invalid request", "The request is invalid"); }

    @ExceptionHandler(UnsupportedContentTypeException.class)
    ProblemDetail unsupportedContentType() {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", "Content-Type must be application/json");
    }
    @ExceptionHandler(EndpointUnsupportedContentTypeException.class)
    ProblemDetail endpointUnsupportedContentType() {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", "Content-Type must be application/json");
    }
    @ExceptionHandler(UnauthorizedException.class)
    ProblemDetail unauthorized() { return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "A valid API key is required"); }

    @ExceptionHandler(PayloadTooLargeException.class)
    ProblemDetail payloadTooLarge() {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Request body too large", "Request body must not exceed 65536 bytes");
    }
    @ExceptionHandler(EndpointPayloadTooLargeException.class)
    ProblemDetail endpointPayloadTooLarge() {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Request body too large", "Request body must not exceed 65536 bytes");
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail idempotencyConflict() {
        return problem(HttpStatus.CONFLICT, "Idempotency conflict", "Idempotency-Key was previously used with a different request");
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> rateLimited() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "60")
                .body(problem(HttpStatus.TOO_MANY_REQUESTS, "Too many requests", "Tenant request limit exceeded; retry after 60 seconds"));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ProblemDetail databaseUnavailable(RuntimeException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable", "Event storage is temporarily unavailable; retry the same request");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpectedFailure() {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "The request could not be completed");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://relay.dev/problems/" + status.value()));
        problem.setTitle(title);
        return problem;
    }
}
