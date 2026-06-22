package com.financialapp.bank;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class InMemoryBankingStore {
    private final Set<Account> accounts = new HashSet<>();
    private final Set<Transaction> transactions = new TreeSet<>();
    private final Deque<String> auditTrail = new ArrayDeque<>();
    private int nextTransactionId = 1;

    public boolean addAccount(Account account) {
        boolean added = accounts.add(account);
        if (added) {
            auditTrail.addLast("Account created: " + account.getAccountNumber());
        }
        return added;
    }

    public Account findAccount(int accountNumber) {
        for (Account account : accounts) {
            if (account.getAccountNumber() == accountNumber) {
                return account;
            }
        }
        return null;
    }

    public boolean deposit(int accountNumber, BigDecimal amount) {
        Account account = findAccount(accountNumber);
        if (account == null) {
            return false;
        }
        account.deposit(amount);
        addTransaction(accountNumber, TransactionType.DEPOSIT, amount);
        auditTrail.addLast("Deposit into account: " + accountNumber);
        return true;
    }

    public boolean withdraw(int accountNumber, BigDecimal amount) {
        Account account = findAccount(accountNumber);
        if (account == null || !account.withdraw(amount)) {
            return false;
        }
        addTransaction(accountNumber, TransactionType.WITHDRAWAL, amount);
        auditTrail.addLast("Withdrawal from account: " + accountNumber);
        return true;
    }

    public boolean transfer(int fromNumber, int toNumber, BigDecimal amount) {
        Account from = findAccount(fromNumber);
        Account to = findAccount(toNumber);
        if (from == null || to == null || !from.withdraw(amount)) {
            return false;
        }
        to.deposit(amount);
        addTransaction(fromNumber, TransactionType.TRANSFER, amount);
        addTransaction(toNumber, TransactionType.TRANSFER, amount);
        auditTrail.addLast("Transfer from " + fromNumber + " to " + toNumber);
        return true;
    }

    public Set<Account> getAccounts() {
        return accounts;
    }

    public Set<Transaction> getTransactions() {
        return transactions;
    }

    public Deque<String> getAuditTrail() {
        return auditTrail;
    }

    private void addTransaction(int accountNumber, TransactionType type, BigDecimal amount) {
        transactions.add(new Transaction(
                nextTransactionId++,
                accountNumber,
                type,
                amount,
                LocalDateTime.now()
        ));
    }
}
