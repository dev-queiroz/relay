package dev.queiroz.relay.api.pagination;

import java.util.UUID;
public record AttemptCursor(int sequenceNumber, UUID id) { }
