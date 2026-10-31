package dev.queiroz.relay.delivery.application;
import java.util.UUID;
public record ClaimedDelivery(UUID deliveryId, String tenantId, UUID eventId, UUID endpointId, String destinationUrl, int attemptNumber, UUID leaseToken) {}
