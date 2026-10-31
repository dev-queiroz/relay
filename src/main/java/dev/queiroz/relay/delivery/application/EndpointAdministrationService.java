package dev.queiroz.relay.delivery.application;

import dev.queiroz.relay.delivery.domain.Endpoint;
import dev.queiroz.relay.ingestion.domain.TenantId;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndpointAdministrationService {
    private final EndpointStore store; private final LocalTargetPolicy targets; private final Clock clock; private final EndpointSigningKeyStore keys;
    public EndpointAdministrationService(EndpointStore store, LocalTargetPolicy targets, Clock clock, EndpointSigningKeyStore keys) { this.store = store; this.targets = targets; this.clock = clock; this.keys = keys; }
    @Transactional public Endpoint register(TenantId tenant, String name, String destination) { if (name == null || name.isBlank()) throw new IllegalArgumentException("endpoint name is required"); Endpoint endpoint = store.create(new Endpoint(UUID.randomUUID(), tenant, name, targets.validate(destination), true, clock.instant())); keys.rotate(tenant, endpoint.id()); return endpoint; }
    @Transactional public String rotateSigningSecret(TenantId tenant, UUID endpointId) { return keys.rotate(tenant, endpointId); }
    public List<Endpoint> enabledFor(TenantId tenant) { return store.enabledFor(tenant); }
    public boolean setEnabled(TenantId tenant, UUID id, boolean enabled) { return store.setEnabled(tenant, id, enabled); }
}
