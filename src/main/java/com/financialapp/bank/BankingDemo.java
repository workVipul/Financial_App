package com.financialapp.bank;

import java.math.BigDecimal;
import java.util.Currency;

public final class BankingDemo {
    private BankingDemo() {
    }

    public static void main(String[] args) {
        InMemoryBankingStore store = new InMemoryBankingStore();
        Customer customer = store.createCustomer("Avery Morgan", "avery@example.com");
        Account checking = store.openAccount(
                customer.id(),
                AccountType.CHECKING,
                Currency.getInstance("USD"),
                new BigDecimal("2500.00")
        );
        Account savings = store.openAccount(
                customer.id(),
                AccountType.SAVINGS,
                Currency.getInstance("USD"),
                new BigDecimal("500.00")
        );

        store.transfer(
                checking.id(),
                savings.id(),
                new BigDecimal("300.00"),
                "Monthly savings",
                "demo-transfer-1"
        );

        System.out.printf(
                "Checking: %s %s%nSavings: %s %s%nTransactions: %d%n",
                checking.currency(),
                checking.balance(),
                savings.currency(),
                savings.balance(),
                store.statistics().transactions()
        );
    }
}
