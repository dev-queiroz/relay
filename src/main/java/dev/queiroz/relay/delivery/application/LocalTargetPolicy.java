package dev.queiroz.relay.delivery.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class LocalTargetPolicy {
    private final Set<String> allowedOrigins;
    public LocalTargetPolicy(@Value("${relay.delivery.local-target-allowlist}") String allowlist) {
        allowedOrigins = Arrays.stream(allowlist.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(URI::create).map(this::origin).collect(Collectors.toUnmodifiableSet());
    }
    public URI validate(String value) {
        try {
            URI uri = URI.create(value);
            if (!uri.isAbsolute() || uri.getUserInfo() != null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || !allowedOrigins.contains(origin(uri))) throw new IllegalArgumentException("destination must be an allowlisted HTTP(S) fixture URL without credentials");
            return uri;
        } catch (IllegalArgumentException exception) { throw new IllegalArgumentException("destination must be an allowlisted HTTP(S) fixture URL without credentials"); }
    }
    private String origin(URI uri) { if (uri.getHost() == null) throw new IllegalArgumentException(); return uri.getScheme().toLowerCase() + "://" + uri.getHost().toLowerCase() + ":" + (uri.getPort() == -1 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort()); }
}
