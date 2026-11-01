package dev.queiroz.relay.delivery.web;

public class EndpointInvalidRequestException extends RuntimeException {
    public EndpointInvalidRequestException() { super("Endpoint request is invalid"); }
}
