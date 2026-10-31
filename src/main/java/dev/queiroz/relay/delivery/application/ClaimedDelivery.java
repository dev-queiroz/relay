package dev.queiroz.relay.delivery.application;
import java.util.UUID;
public record ClaimedDelivery(UUID deliveryId, UUID eventId, String destinationUrl, int attemptNumber, UUID leaseToken) {}
