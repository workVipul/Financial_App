# In-Memory Banking Store

A Java 21 banking data store that keeps all state in process memory. It focuses on choosing
collections by access pattern rather than persistence, HTTP APIs, or user interfaces.

## Features

- Customer registration with case-insensitive email uniqueness
- Checking, savings, and credit accounts
- Deposits, withdrawals, and atomic same-currency transfers
- Idempotent transfer requests
- Chronological account transaction history and time-range queries
- Scheduled payment processing in due-time order
- Capacity-bounded audit history
- Account and store statistics
- Read/write locking around compound operations

## Collection Choices and Complexity

`n` means the number of elements in the relevant collection, `k` the number of returned
items, and `m` the number of accounts owned by one customer.

| Data / access pattern | Collection | Read | Write | Why |
|---|---|---:|---:|---|
| Customer by ID | `HashMap<UUID, Customer>` | Average `O(1)` | Average `O(1)` | IDs are exact-match keys; ordering provides no value. |
| Unique customer email | `HashMap<String, UUID>` | Average `O(1)` | Average `O(1)` | Fast uniqueness checks and reverse lookup without scanning customers. |
| Account details / uniqueness | `HashSet<Account>` | Average `O(1)` membership | Average `O(1)` | Account identity is an immutable UUID, making hash membership safe even as balances change. |
| Account by ID | `HashMap<UUID, Account>` | Average `O(1)` | Average `O(1)` | A second index avoids an `O(n)` scan of the account set for normal lookups. |
| Accounts owned by customer | `HashMap<UUID, HashSet<UUID>>` | `O(m)` to return all | Average `O(1)` per association | Efficient one-to-many index with no duplicate account IDs. |
| Transactions in time order | `TreeSet<Transaction>` | Range query `O(log n + k)` | `O(log n)` | Maintains sorting continuously and supports bounded time-range views. Timestamp plus UUID is a total order, so simultaneous transactions are retained. |
| Audit trail | `ArrayDeque<AuditEvent>` | Newest/oldest `O(1)`; snapshot `O(n)` | Amortized `O(1)` | FIFO eviction and append are exactly the operations a bounded audit buffer needs. |
| Scheduled payments | `PriorityQueue<PaymentInstruction>` | Next due `O(1)` | Add/remove `O(log n)` | Only the earliest payment must be available; fully sorting every read would cost more. |
| Counts by account type | `EnumMap<AccountType, Integer>` | `O(1)` | `O(1)` | Compact, type-safe array-backed storage optimized for enum keys. |
| Transfer idempotency cache | access-ordered `LinkedHashMap<String, UUID>` | Average `O(1)` | Average `O(1)` | Combines hash lookup with deterministic least-recently-used eviction at a fixed capacity. |

Hash collection operations are average-case complexities; pathological hash collisions can
degrade them to `O(n)`. Returned collections are immutable snapshots so callers cannot mutate
the store's indexes.

## Consistency Model

The store uses a `ReentrantReadWriteLock`. Independent reads can proceed together, while every
mutation receives the write lock. A transfer validates both accounts and funds before applying
the debit and credit under one lock, so another thread cannot observe a half-completed transfer.

This is intentionally not durable: restarting the JVM loses all data. A production system would
also need persistent transaction boundaries, authentication and authorization, encryption,
regulatory controls, observability, and disaster recovery.

## Run

```shell
mvn test
mvn package
java -cp target/classes com.financialapp.bank.BankingDemo
```

Example output:

```text
Checking: USD 2200.00
Savings: USD 800.00
Transactions: 4
```
