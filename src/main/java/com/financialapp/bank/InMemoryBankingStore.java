package com.financialapp.bank;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class InMemoryBankingStore {
    private static final int DEFAULT_AUDIT_CAPACITY = 1_000;
    private static final int DEFAULT_IDEMPOTENCY_CAPACITY = 10_000;

    private final Map<UUID, Customer> customersById = new HashMap<>();
    private final Map<String, UUID> customerIdByEmail = new HashMap<>();
    private final Set<Account> accounts = new HashSet<>();
    private final Map<UUID, Account> accountsById = new HashMap<>();
    private final Map<UUID, Set<UUID>> accountIdsByCustomer = new HashMap<>();
    private final NavigableSet<Transaction> transactions = new TreeSet<>();
    private final Map<UUID, NavigableSet<Transaction>> transactionsByAccount = new HashMap<>();
    private final Deque<AuditEvent> auditTrail = new ArrayDeque<>();
    private final PriorityQueue<PaymentInstruction> scheduledPayments = new PriorityQueue<>();
    private final EnumMap<AccountType, Integer> accountCounts = new EnumMap<>(AccountType.class);
    private final Map<String, UUID> transferIdempotencyKeys;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Clock clock;
    private final int auditCapacity;

    public InMemoryBankingStore() {
        this(Clock.systemUTC(), DEFAULT_AUDIT_CAPACITY, DEFAULT_IDEMPOTENCY_CAPACITY);
    }

    public InMemoryBankingStore(Clock clock, int auditCapacity, int idempotencyCapacity) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (auditCapacity <= 0 || idempotencyCapacity <= 0) {
            throw new IllegalArgumentException("capacities must be positive");
        }
        this.auditCapacity = auditCapacity;
        this.transferIdempotencyKeys = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, UUID> eldest) {
                return size() > idempotencyCapacity;
            }
        };
        for (AccountType type : AccountType.values()) {
            accountCounts.put(type, 0);
        }
    }

    public Customer createCustomer(String fullName, String email) {
        lock.writeLock().lock();
        try {
            String normalizedEmail = normalizeEmail(email);
            if (customerIdByEmail.containsKey(normalizedEmail)) {
                throw new IllegalArgumentException("A customer with this email already exists");
            }
            Customer customer = new Customer(UUID.randomUUID(), fullName, normalizedEmail, clock.instant());
            customersById.put(customer.id(), customer);
            customerIdByEmail.put(customer.email(), customer.id());
            appendAudit("CUSTOMER_CREATED", customer.id(), Map.of("customerId", customer.id().toString()));
            return customer;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Account openAccount(
            UUID customerId,
            AccountType type,
            Currency currency,
            BigDecimal openingBalance
    ) {
        lock.writeLock().lock();
        try {
            requireCustomer(customerId);
            Account account = new Account(
                    UUID.randomUUID(),
                    customerId,
                    type,
                    currency,
                    openingBalance,
                    clock.instant()
            );
            accounts.add(account);
            accountsById.put(account.id(), account);
            accountIdsByCustomer.computeIfAbsent(customerId, ignored -> new HashSet<>()).add(account.id());
            accountCounts.merge(type, 1, Integer::sum);
            appendAudit("ACCOUNT_OPENED", customerId, Map.of("accountId", account.id().toString()));

            if (openingBalance.signum() > 0) {
                recordTransaction(account, null, TransactionType.DEPOSIT, openingBalance, "Opening balance");
            }
            return account;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<Customer> findCustomer(UUID customerId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(customersById.get(customerId));
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<Customer> findCustomerByEmail(String email) {
        lock.readLock().lock();
        try {
            UUID customerId = customerIdByEmail.get(normalizeEmail(email));
            return Optional.ofNullable(customerId).map(customersById::get);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<Account> findAccount(UUID accountId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(accountsById.get(accountId));
        } finally {
            lock.readLock().unlock();
        }
    }

    public Set<Account> accountsForCustomer(UUID customerId) {
        lock.readLock().lock();
        try {
            Set<UUID> accountIds = accountIdsByCustomer.getOrDefault(customerId, Set.of());
            Set<Account> result = new HashSet<>(accountIds.size());
            for (UUID accountId : accountIds) {
                result.add(accountsById.get(accountId));
            }
            return Set.copyOf(result);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Transaction deposit(UUID accountId, BigDecimal amount, String description) {
        lock.writeLock().lock();
        try {
            Account account = requireAccount(accountId);
            account.credit(amount);
            Transaction transaction = recordTransaction(
                    account,
                    null,
                    TransactionType.DEPOSIT,
                    amount,
                    description
            );
            appendAudit("DEPOSIT", account.customerId(), transactionDetails(transaction));
            return transaction;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Transaction withdraw(UUID accountId, BigDecimal amount, String description) {
        lock.writeLock().lock();
        try {
            Account account = requireAccount(accountId);
            account.debit(amount);
            Transaction transaction = recordTransaction(
                    account,
                    null,
                    TransactionType.WITHDRAWAL,
                    amount,
                    description
            );
            appendAudit("WITHDRAWAL", account.customerId(), transactionDetails(transaction));
            return transaction;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public UUID transfer(
            UUID fromAccountId,
            UUID toAccountId,
            BigDecimal amount,
            String description,
            String idempotencyKey
    ) {
        lock.writeLock().lock();
        try {
            String key = requireText(idempotencyKey, "idempotencyKey");
            UUID existingTransferId = transferIdempotencyKeys.get(key);
            if (existingTransferId != null) {
                return existingTransferId;
            }
            if (fromAccountId.equals(toAccountId)) {
                throw new IllegalArgumentException("Source and destination accounts must differ");
            }

            Account from = requireAccount(fromAccountId);
            Account to = requireAccount(toAccountId);
            if (!from.currency().equals(to.currency())) {
                throw new IllegalArgumentException("Cross-currency transfers are not supported");
            }

            from.requireActive();
            to.requireActive();
            from.debit(amount);
            to.credit(amount);
            UUID transferId = UUID.randomUUID();
            recordTransaction(
                    UUID.randomUUID(),
                    from,
                    to.id(),
                    TransactionType.TRANSFER,
                    amount,
                    "Outgoing: " + safeDescription(description)
            );
            recordTransaction(
                    UUID.randomUUID(),
                    to,
                    from.id(),
                    TransactionType.TRANSFER,
                    amount,
                    "Incoming: " + safeDescription(description)
            );
            transferIdempotencyKeys.put(key, transferId);
            appendAudit("TRANSFER", from.customerId(), Map.of(
                    "transferId", transferId.toString(),
                    "fromAccountId", from.id().toString(),
                    "toAccountId", to.id().toString(),
                    "amount", amount.toPlainString()
            ));
            return transferId;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<Transaction> transactionHistory(UUID accountId, Instant fromInclusive, Instant toExclusive) {
        Objects.requireNonNull(fromInclusive, "fromInclusive");
        Objects.requireNonNull(toExclusive, "toExclusive");
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("fromInclusive must be before toExclusive");
        }

        lock.readLock().lock();
        try {
            requireAccount(accountId);
            NavigableSet<Transaction> accountTransactions =
                    transactionsByAccount.getOrDefault(accountId, new TreeSet<>());
            Transaction lower = boundaryTransaction(accountId, fromInclusive, new UUID(0, 0));
            Transaction upper = boundaryTransaction(accountId, toExclusive, new UUID(0, 0));
            return List.copyOf(accountTransactions.subSet(lower, true, upper, false));
        } finally {
            lock.readLock().unlock();
        }
    }

    public PaymentInstruction schedulePayment(
            UUID fromAccountId,
            UUID toAccountId,
            BigDecimal amount,
            Instant executeAt,
            String description
    ) {
        lock.writeLock().lock();
        try {
            requireAccount(fromAccountId);
            requireAccount(toAccountId);
            PaymentInstruction instruction = new PaymentInstruction(
                    UUID.randomUUID(),
                    fromAccountId,
                    toAccountId,
                    amount,
                    executeAt,
                    description
            );
            scheduledPayments.add(instruction);
            appendAudit("PAYMENT_SCHEDULED", null, Map.of("paymentId", instruction.id().toString()));
            return instruction;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<UUID> processDuePayments(Instant now) {
        Objects.requireNonNull(now, "now");
        lock.writeLock().lock();
        try {
            List<UUID> processed = new ArrayList<>();
            while (!scheduledPayments.isEmpty() && !scheduledPayments.peek().executeAt().isAfter(now)) {
                PaymentInstruction payment = scheduledPayments.remove();
                transfer(
                        payment.fromAccountId(),
                        payment.toAccountId(),
                        payment.amount(),
                        payment.description(),
                        "scheduled:" + payment.id()
                );
                processed.add(payment.id());
            }
            return List.copyOf(processed);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<AuditEvent> recentAuditEvents() {
        lock.readLock().lock();
        try {
            return List.copyOf(auditTrail);
        } finally {
            lock.readLock().unlock();
        }
    }

    public StoreStatistics statistics() {
        lock.readLock().lock();
        try {
            return new StoreStatistics(
                    customersById.size(),
                    accounts.size(),
                    transactions.size(),
                    scheduledPayments.size(),
                    auditTrail.size(),
                    accountCounts
            );
        } finally {
            lock.readLock().unlock();
        }
    }

    private Transaction recordTransaction(
            Account account,
            UUID relatedAccountId,
            TransactionType type,
            BigDecimal amount,
            String description
    ) {
        return recordTransaction(
                UUID.randomUUID(),
                account,
                relatedAccountId,
                type,
                amount,
                description
        );
    }

    private Transaction recordTransaction(
            UUID transactionId,
            Account account,
            UUID relatedAccountId,
            TransactionType type,
            BigDecimal amount,
            String description
    ) {
        Transaction transaction = new Transaction(
                transactionId,
                account.id(),
                relatedAccountId,
                type,
                amount,
                account.currency(),
                clock.instant(),
                description
        );
        transactions.add(transaction);
        transactionsByAccount
                .computeIfAbsent(account.id(), ignored -> new TreeSet<>())
                .add(transaction);
        return transaction;
    }

    private void appendAudit(String action, UUID actorId, Map<String, String> details) {
        if (auditTrail.size() == auditCapacity) {
            auditTrail.removeFirst();
        }
        auditTrail.addLast(new AuditEvent(UUID.randomUUID(), clock.instant(), action, actorId, details));
    }

    private Customer requireCustomer(UUID customerId) {
        Customer customer = customersById.get(Objects.requireNonNull(customerId, "customerId"));
        if (customer == null) {
            throw new IllegalArgumentException("Customer not found: " + customerId);
        }
        return customer;
    }

    private Account requireAccount(UUID accountId) {
        Account account = accountsById.get(Objects.requireNonNull(accountId, "accountId"));
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        return account;
    }

    private static String normalizeEmail(String email) {
        return requireText(email, "email").toLowerCase();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String safeDescription(String description) {
        return description == null ? "" : description.trim();
    }

    private static Map<String, String> transactionDetails(Transaction transaction) {
        return Map.of(
                "transactionId", transaction.id().toString(),
                "accountId", transaction.accountId().toString(),
                "amount", transaction.amount().toPlainString()
        );
    }

    private static Transaction boundaryTransaction(UUID accountId, Instant time, UUID id) {
        return new Transaction(
                id,
                accountId,
                null,
                TransactionType.DEPOSIT,
                BigDecimal.ONE,
                Currency.getInstance("USD"),
                time,
                ""
        );
    }
}
