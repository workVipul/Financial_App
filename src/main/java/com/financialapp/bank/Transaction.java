package com.financialapp.bank;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Transaction(
        int id,
        int accountNumber,
        TransactionType type,
        BigDecimal amount,
        LocalDateTime time
) implements Comparable<Transaction> {

    @Override
    public int compareTo(Transaction other) {
        int result = time.compareTo(other.time);
        return result != 0 ? result : Integer.compare(id, other.id);
    }
}
