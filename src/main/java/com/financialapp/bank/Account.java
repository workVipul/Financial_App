package com.financialapp.bank;

import java.math.BigDecimal;

public class Account {
    private final int accountNumber;
    private final String customerName;
    private final AccountType type;
    private BigDecimal balance;

    public Account(int accountNumber, String customerName, AccountType type, BigDecimal balance) {
        this.accountNumber = accountNumber;
        this.customerName = customerName;
        this.type = type;
        this.balance = balance;
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void deposit(BigDecimal amount) {
        balance = balance.add(amount);
    }

    public boolean withdraw(BigDecimal amount) {
        if (balance.compareTo(amount) >= 0) {
            balance = balance.subtract(amount);
            return true;
        }
        return false;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Account account && accountNumber == account.accountNumber;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(accountNumber);
    }

    @Override
    public String toString() {
        return accountNumber + " | " + customerName + " | " + type + " | Balance: " + balance;
    }
}
