package dev.queiroz.relay.ingestion.application;

public final class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("idempotency key has already been used for a different request");
    }
}
