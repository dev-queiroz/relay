package dev.queiroz.relay.ingestion.application;

public final class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException() { super("tenant rate limit exceeded"); }
}
