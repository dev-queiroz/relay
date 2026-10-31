package dev.queiroz.relay.delivery.application;

import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;

/** Resolves destinations at registration and again immediately before dispatch. */
@Component
public class LocalTargetPolicy {
    private final DnsResolver dns;

    public LocalTargetPolicy() { this(InetAddress::getAllByName); }
    public LocalTargetPolicy(DnsResolver dns) { this.dns = dns; }

    public URI validate(String value) {
        try {
            URI uri = URI.create(value);
            if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getRawFragment() != null) throw rejected();
            InetAddress[] addresses = dns.resolve(uri.getHost());
            if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(this::isNotPublic)) throw rejected();
            return uri;
        } catch (IllegalArgumentException | UnknownHostException exception) { throw rejected(); }
    }

    private boolean isNotPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        byte[] raw = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(raw[0]), second = Byte.toUnsignedInt(raw[1]);
            return first == 0 || first >= 224 || (first == 100 && second >= 64 && second <= 127)
                    || (first == 192 && second == 0) || (first == 198 && (second == 18 || second == 19));
        }
        if (address instanceof Inet6Address) {
            int first = Byte.toUnsignedInt(raw[0]), second = Byte.toUnsignedInt(raw[1]);
            return (first & 0xfe) == 0xfc || (first == 0xfe && (second & 0xc0) == 0xc0);
        }
        return true;
    }

    private IllegalArgumentException rejected() { return new IllegalArgumentException("destination must be an HTTPS URL resolving only to public addresses"); }

    @FunctionalInterface public interface DnsResolver { InetAddress[] resolve(String host) throws UnknownHostException; }
}
