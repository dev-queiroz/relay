package dev.queiroz.relay.api.pagination;

public class InvalidCursorException extends RuntimeException {
    public InvalidCursorException() { super("cursor is invalid or expired"); }
}
