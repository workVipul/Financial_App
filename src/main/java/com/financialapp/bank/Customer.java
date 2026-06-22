package com.financialapp.bank;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Customer(
        UUID id,
        String fullName,
        String email,
        Instant createdAt
) {
    public Customer {
        Objects.requireNonNull(id, "id");
        fullName = requireText(fullName, "fullName");
        email = requireText(email, "email").toLowerCase();
        Objects.requireNonNull(createdAt, "createdAt");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
