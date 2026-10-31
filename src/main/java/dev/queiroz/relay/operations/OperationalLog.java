package dev.queiroz.relay.operations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Emits only fixed event names and correlation identifiers; never pass payloads, URLs, headers, or exceptions here. */
@Component
public class OperationalLog {
    private static final Logger LOG = LoggerFactory.getLogger(OperationalLog.class);

    public void eventAccepted(String tenantId, UUID eventId) {
        LOG.atInfo().addKeyValue("tenantId", tenantId).addKeyValue("eventId", eventId).log("event.accepted");
    }

    public void deliveryStarted(String tenantId, UUID eventId, UUID deliveryId) {
        withDeliveryContext(tenantId, eventId, deliveryId, () -> LOG.info("delivery.dispatch.started"));
    }

    public void deliveryCompleted(String tenantId, UUID eventId, UUID deliveryId, int status) {
        withDeliveryContext(tenantId, eventId, deliveryId, () -> LOG.atInfo().addKeyValue("responseStatus", status).log("delivery.dispatch.completed"));
    }

    public void deliveryFailed(String tenantId, UUID eventId, UUID deliveryId) {
        withDeliveryContext(tenantId, eventId, deliveryId, () -> LOG.warn("delivery.dispatch.failed errorCode=TRANSPORT_ERROR"));
    }

    private void withDeliveryContext(String tenantId, UUID eventId, UUID deliveryId, Runnable log) {
        try (MDC.MDCCloseable tenant = MDC.putCloseable("tenantId", tenantId);
             MDC.MDCCloseable event = MDC.putCloseable("eventId", eventId.toString());
             MDC.MDCCloseable delivery = MDC.putCloseable("deliveryId", deliveryId.toString())) {
            log.run();
        }
    }
}
