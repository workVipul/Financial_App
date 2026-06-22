package com.financialapp.bank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryBankingStoreTest {
    private static final Instant NOW = Instant.parse("2026-06-22T10:00:00Z");
    private static final Currency USD = Currency.getInstance("USD");

    private InMemoryBankingStore store;
    private Customer customer;

    @BeforeEach
    void setUp() {
        store = new InMemoryBankingStore(Clock.fixed(NOW, ZoneOffset.UTC), 3, 10);
        customer = store.createCustomer("Jordan Lee", "Jordan@example.com");
    }

    @Test
    void createsCustomersWithUniqueNormalizedEmails() {
        assertEquals(customer, store.findCustomerByEmail("JORDAN@example.com").orElseThrow());

        assertThrows(
                IllegalArgumentException.class,
                () -> store.createCustomer("Another Jordan", " jordan@example.com ")
        );
    }

    @Test
    void storesAccountsInCustomerIndexAndCountsTypes() {
        Account checking = open(AccountType.CHECKING, "100.00");
        Account savings = open(AccountType.SAVINGS, "50.00");

        assertEquals(2, store.accountsForCustomer(customer.id()).size());
        assertEquals(checking, store.findAccount(checking.id()).orElseThrow());
        assertEquals(1, store.statistics().accountsByType().get(AccountType.CHECKING));
        assertEquals(1, store.statistics().accountsByType().get(AccountType.SAVINGS));
        assertEquals(savings.balance(), new BigDecimal("50.00"));
    }

    @Test
    void transfersAtomicallyAndHonorsIdempotencyKey() {
        Account checking = open(AccountType.CHECKING, "500.00");
        Account savings = open(AccountType.SAVINGS, "100.00");

        var firstId = store.transfer(
                checking.id(), savings.id(), new BigDecimal("75.00"), "Save", "request-123"
        );
        var replayId = store.transfer(
                checking.id(), savings.id(), new BigDecimal("75.00"), "Save", "request-123"
        );

        assertEquals(firstId, replayId);
        assertEquals(new BigDecimal("425.00"), checking.balance());
        assertEquals(new BigDecimal("175.00"), savings.balance());
    }

    @Test
    void rejectsTransfersWithInsufficientFundsWithoutChangingBalances() {
        Account checking = open(AccountType.CHECKING, "20.00");
        Account savings = open(AccountType.SAVINGS, "10.00");

        assertThrows(
                IllegalStateException.class,
                () -> store.transfer(
                        checking.id(), savings.id(), new BigDecimal("30.00"), "Too much", "request-1"
                )
        );
        assertEquals(new BigDecimal("20.00"), checking.balance());
        assertEquals(new BigDecimal("10.00"), savings.balance());
    }

    @Test
    void returnsTransactionHistoryWithinTimeRange() {
        Account account = open(AccountType.CHECKING, "100.00");
        store.deposit(account.id(), new BigDecimal("25.00"), "Refund");
        store.withdraw(account.id(), new BigDecimal("10.00"), "Coffee");

        List<Transaction> history = store.transactionHistory(
                account.id(), NOW.minusSeconds(1), NOW.plusSeconds(1)
        );

        assertEquals(3, history.size());
        assertEquals(
                Map.of(TransactionType.DEPOSIT, 2L, TransactionType.WITHDRAWAL, 1L),
                history.stream().collect(Collectors.groupingBy(Transaction::type, Collectors.counting()))
        );
    }

    @Test
    void processesScheduledPaymentsInExecutionOrder() {
        Account checking = open(AccountType.CHECKING, "500.00");
        Account savings = open(AccountType.SAVINGS, "0.00");
        PaymentInstruction later = store.schedulePayment(
                checking.id(), savings.id(), new BigDecimal("20.00"), NOW.plusSeconds(20), "Later"
        );
        PaymentInstruction earlier = store.schedulePayment(
                checking.id(), savings.id(), new BigDecimal("10.00"), NOW.plusSeconds(10), "Earlier"
        );

        assertEquals(
                List.of(earlier.id(), later.id()),
                store.processDuePayments(NOW.plusSeconds(30))
        );
        assertEquals(new BigDecimal("470.00"), checking.balance());
        assertEquals(new BigDecimal("30.00"), savings.balance());
    }

    @Test
    void boundsAuditTrailAndEvictsOldestEvents() {
        open(AccountType.CHECKING, "0.00");
        open(AccountType.SAVINGS, "0.00");
        open(AccountType.CREDIT, "0.00");

        assertEquals(3, store.recentAuditEvents().size());
        assertEquals(
                List.of("ACCOUNT_OPENED", "ACCOUNT_OPENED", "ACCOUNT_OPENED"),
                store.recentAuditEvents().stream().map(AuditEvent::action).toList()
        );
    }

    private Account open(AccountType type, String openingBalance) {
        return store.openAccount(customer.id(), type, USD, new BigDecimal(openingBalance));
    }
}
