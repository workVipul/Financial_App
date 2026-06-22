package com.financialapp.bank;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AuditEvent(
        UUID id,
        Instant timestamp,
        String action,
        UUID actorId,
        Map<String, String> details
) {
    public AuditEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(timestamp, "timestamp");
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        action = action.trim();
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
