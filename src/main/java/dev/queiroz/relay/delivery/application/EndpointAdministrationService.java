package dev.queiroz.relay.delivery.application;

import dev.queiroz.relay.api.pagination.KeysetCursor;
import dev.queiroz.relay.delivery.domain.Endpoint;
import dev.queiroz.relay.ingestion.domain.TenantId;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndpointAdministrationService {
    private final EndpointStore store; private final LocalTargetPolicy targets; private final Clock clock; private final EndpointSigningKeyStore keys;
    public EndpointAdministrationService(EndpointStore store, LocalTargetPolicy targets, Clock clock, EndpointSigningKeyStore keys) { this.store = store; this.targets = targets; this.clock = clock; this.keys = keys; }
    @Transactional public RegisteredEndpoint register(TenantId tenant, String name, String destination) {
        Endpoint endpoint = store.create(new Endpoint(UUID.randomUUID(), tenant, validatedName(name), targets.validate(destination), true, clock.instant()));
        return new RegisteredEndpoint(endpoint, keys.rotate(tenant, endpoint.id()));
    }
    @Transactional public Optional<String> rotateSigningSecret(TenantId tenant, UUID endpointId) {
        if (store.find(tenant, endpointId).isEmpty()) return Optional.empty();
        return Optional.of(keys.rotate(tenant, endpointId));
    }
    public Optional<Endpoint> find(TenantId tenant, UUID endpointId) { return store.find(tenant, endpointId); }
    public List<Endpoint> list(TenantId tenant, KeysetCursor cursor, int limit) {
        return store.page(tenant, cursor == null ? null : cursor.createdAt(), cursor == null ? null : cursor.id(), limit);
    }
    @Transactional public Optional<Endpoint> update(TenantId tenant, UUID endpointId, String name, String destination, Boolean enabled) {
        URI validatedDestination = destination == null ? null : targets.validate(destination);
        return store.update(tenant, endpointId, name == null ? null : validatedName(name), validatedDestination, enabled);
    }
    public List<Endpoint> enabledFor(TenantId tenant) { return store.enabledFor(tenant); }
    public boolean setEnabled(TenantId tenant, UUID id, boolean enabled) { return store.setEnabled(tenant, id, enabled); }
    private String validatedName(String name) {
        if (name == null) throw new IllegalArgumentException("endpoint name is required");
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 128) throw new IllegalArgumentException("endpoint name must be between 1 and 128 characters");
        return trimmed;
    }
    public record RegisteredEndpoint(Endpoint endpoint, String signingSecret) { }
}
