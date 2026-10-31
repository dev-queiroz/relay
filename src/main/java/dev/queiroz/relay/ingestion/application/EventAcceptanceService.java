package dev.queiroz.relay.ingestion.application;

import dev.queiroz.relay.ingestion.domain.Event;
import dev.queiroz.relay.ingestion.domain.IdempotencyRecord;
import dev.queiroz.relay.delivery.application.DeliveryStore;
import dev.queiroz.relay.delivery.application.EndpointStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Service
public class EventAcceptanceService {

    private final EventStore eventStore;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final EndpointStore endpointStore;
    private final DeliveryStore deliveryStore;

    public EventAcceptanceService(
            EventStore eventStore,
            TransactionTemplate transactionTemplate,
            Clock clock,
            EndpointStore endpointStore,
            DeliveryStore deliveryStore
    ) {
        this.eventStore = eventStore;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.endpointStore = endpointStore;
        this.deliveryStore = deliveryStore;
    }

    public AcceptedEvent accept(AcceptEventCommand command) {
        try {
            return transactionTemplate.execute(status -> createAcceptedEvent(command));
        } catch (DataIntegrityViolationException exception) {
            return resolveIdempotencyViolation(command, exception);
        }
    }

    private AcceptedEvent createAcceptedEvent(AcceptEventCommand command) {
        Event event = Event.accept(command.tenantId(), command.type(), command.payload(), clock);
        IdempotencyRecord record = IdempotencyRecord.create(
                command.tenantId(),
                command.idempotencyKey(),
                command.requestFingerprint(),
                event.id(),
                clock
        );

        eventStore.insert(event);
        eventStore.insert(record);
        endpointStore.enabledFor(command.tenantId()).forEach(endpoint -> deliveryStore.schedule(event, endpoint, clock));
        return new AcceptedEvent(event.id());
    }

    private AcceptedEvent resolveIdempotencyViolation(
            AcceptEventCommand command,
            DataIntegrityViolationException originalException
    ) {
        return eventStore.findIdempotencyRecord(command.tenantId(), command.idempotencyKey())
                .map(existing -> {
                    if (!existing.requestFingerprint().equals(command.requestFingerprint())) {
                        throw new IdempotencyConflictException();
                    }
                    return new AcceptedEvent(existing.eventId());
                })
                .orElseThrow(() -> originalException);
    }
}
