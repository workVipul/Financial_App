package com.financialapp.bank;

import java.util.Map;

public record StoreStatistics(
        int customers,
        int accounts,
        int transactions,
        int scheduledPayments,
        int auditEvents,
        Map<AccountType, Integer> accountsByType
) {
    public StoreStatistics {
        accountsByType = Map.copyOf(accountsByType);
    }
}
