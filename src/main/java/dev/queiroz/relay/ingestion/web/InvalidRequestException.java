package dev.queiroz.relay.ingestion.web;

final class InvalidRequestException extends RuntimeException {
    InvalidRequestException(String message) {
        super(message);
    }
}
