package dev.queiroz.relay.api.pagination;

import java.time.Instant;
import java.util.UUID;

/** The last tuple in a descending created-at, UUID keyset ordering. */
public record KeysetCursor(Instant createdAt, UUID id) { }
