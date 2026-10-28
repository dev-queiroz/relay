package dev.queiroz.relay.ingestion.domain;

public final class InvalidEventException extends IllegalArgumentException {

    public InvalidEventException(String message) {
        super(message);
    }
}
