package com.financialapp.bank;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentInstruction(
        UUID id,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        Instant executeAt,
        String description
) implements Comparable<PaymentInstruction> {
    public PaymentInstruction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fromAccountId, "fromAccountId");
        Objects.requireNonNull(toAccountId, "toAccountId");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        Objects.requireNonNull(executeAt, "executeAt");
        description = description == null ? "" : description.trim();
    }

    @Override
    public int compareTo(PaymentInstruction other) {
        int byExecutionTime = executeAt.compareTo(other.executeAt);
        return byExecutionTime != 0 ? byExecutionTime : id.compareTo(other.id);
    }
}
