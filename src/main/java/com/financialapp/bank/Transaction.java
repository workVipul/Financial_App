package com.financialapp.bank;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

public record Transaction(
        UUID id,
        UUID accountId,
        UUID relatedAccountId,
        TransactionType type,
        BigDecimal amount,
        Currency currency,
        Instant occurredAt,
        String description
) implements Comparable<Transaction> {
    public Transaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(occurredAt, "occurredAt");
        description = description == null ? "" : description.trim();
    }

    @Override
    public int compareTo(Transaction other) {
        int byTime = occurredAt.compareTo(other.occurredAt);
        return byTime != 0 ? byTime : id.compareTo(other.id);
    }
}
