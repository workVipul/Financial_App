package com.financialapp.bank;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

public final class Account {
    private final UUID id;
    private final UUID customerId;
    private final AccountType type;
    private final Currency currency;
    private final Instant openedAt;
    private BigDecimal balance;
    private AccountStatus status;

    public Account(
            UUID id,
            UUID customerId,
            AccountType type,
            Currency currency,
            BigDecimal openingBalance,
            Instant openedAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.type = Objects.requireNonNull(type, "type");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.balance = requireNonNegative(openingBalance, "openingBalance");
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt");
        this.status = AccountStatus.ACTIVE;
    }

    public UUID id() {
        return id;
    }

    public UUID customerId() {
        return customerId;
    }

    public AccountType type() {
        return type;
    }

    public Currency currency() {
        return currency;
    }

    public BigDecimal balance() {
        return balance;
    }

    public Instant openedAt() {
        return openedAt;
    }

    public AccountStatus status() {
        return status;
    }

    void debit(BigDecimal amount) {
        requireActive();
        BigDecimal value = requirePositive(amount, "amount");
        if (balance.compareTo(value) < 0) {
            throw new IllegalStateException("Insufficient funds");
        }
        balance = balance.subtract(value);
    }

    void credit(BigDecimal amount) {
        requireActive();
        balance = balance.add(requirePositive(amount, "amount"));
    }

    void setStatus(AccountStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    void requireActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }
    }

    private static BigDecimal requirePositive(BigDecimal value, String field) {
        Objects.requireNonNull(value, field);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String field) {
        Objects.requireNonNull(value, field);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Account account && id.equals(account.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
