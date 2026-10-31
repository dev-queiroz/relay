package dev.queiroz.relay.delivery.application;

import dev.queiroz.relay.delivery.domain.Endpoint;
import dev.queiroz.relay.ingestion.domain.TenantId;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class EndpointAdministrationService {
    private final EndpointStore store; private final LocalTargetPolicy targets; private final Clock clock;
    public EndpointAdministrationService(EndpointStore store, LocalTargetPolicy targets, Clock clock) { this.store = store; this.targets = targets; this.clock = clock; }
    public Endpoint register(TenantId tenant, String name, String destination) { if (name == null || name.isBlank()) throw new IllegalArgumentException("endpoint name is required"); return store.create(new Endpoint(UUID.randomUUID(), tenant, name, targets.validate(destination), true, clock.instant())); }
    public List<Endpoint> enabledFor(TenantId tenant) { return store.enabledFor(tenant); }
    public boolean setEnabled(TenantId tenant, UUID id, boolean enabled) { return store.setEnabled(tenant, id, enabled); }
}
