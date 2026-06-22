package com.financialapp.bank;

import java.math.BigDecimal;
import java.util.Scanner;

public class BankingDemo {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        InMemoryBankingStore store = new InMemoryBankingStore();

        store.addAccount(new Account(1001, "Avery Morgan", AccountType.CHECKING, new BigDecimal("2500")));
        store.addAccount(new Account(1002, "Jordan Lee", AccountType.SAVINGS, new BigDecimal("800")));

        int choice = -1;
        while (choice != 0) {
            printMenu();
            choice = readInt(scanner, "Choose an option: ");

            switch (choice) {
                case 1 -> createAccount(scanner, store);
                case 2 -> store.getAccounts().forEach(System.out::println);
                case 3 -> deposit(scanner, store);
                case 4 -> withdraw(scanner, store);
                case 5 -> transfer(scanner, store);
                case 6 -> store.getTransactions().forEach(System.out::println);
                case 7 -> store.getAuditTrail().forEach(System.out::println);
                case 0 -> System.out.println("Goodbye!");
                default -> System.out.println("Invalid option.");
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n============================");
        System.out.println("     SIMPLE BANK SYSTEM");
        System.out.println("============================");
        System.out.println("1. Create account");
        System.out.println("2. View accounts");
        System.out.println("3. Deposit");
        System.out.println("4. Withdraw");
        System.out.println("5. Transfer");
        System.out.println("6. View transactions");
        System.out.println("7. View audit trail");
        System.out.println("0. Exit");
        System.out.println("============================");
    }

    private static void createAccount(Scanner scanner, InMemoryBankingStore store) {
        int number = readInt(scanner, "Account number: ");
        System.out.print("Customer name: ");
        String name = scanner.nextLine();
        int typeNumber = readInt(scanner, "Type (1-Checking, 2-Savings, 3-Credit): ");
        BigDecimal balance = readAmount(scanner, "Opening balance: ");

        AccountType type = switch (typeNumber) {
            case 2 -> AccountType.SAVINGS;
            case 3 -> AccountType.CREDIT;
            default -> AccountType.CHECKING;
        };

        if (store.addAccount(new Account(number, name, type, balance))) {
            System.out.println("Account created.");
        } else {
            System.out.println("Account number already exists.");
        }
    }

    private static void deposit(Scanner scanner, InMemoryBankingStore store) {
        int number = readInt(scanner, "Account number: ");
        BigDecimal amount = readAmount(scanner, "Amount: ");
        System.out.println(store.deposit(number, amount) ? "Deposit successful." : "Account not found.");
    }

    private static void withdraw(Scanner scanner, InMemoryBankingStore store) {
        int number = readInt(scanner, "Account number: ");
        BigDecimal amount = readAmount(scanner, "Amount: ");
        System.out.println(store.withdraw(number, amount)
                ? "Withdrawal successful."
                : "Account not found or insufficient balance.");
    }

    private static void transfer(Scanner scanner, InMemoryBankingStore store) {
        int from = readInt(scanner, "From account: ");
        int to = readInt(scanner, "To account: ");
        BigDecimal amount = readAmount(scanner, "Amount: ");
        System.out.println(store.transfer(from, to, amount)
                ? "Transfer successful."
                : "Transfer failed.");
    }

    private static int readInt(Scanner scanner, String message) {
        while (true) {
            System.out.print(message);
            try {
                return Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException exception) {
                System.out.println("Enter a valid number.");
            }
        }
    }

    private static BigDecimal readAmount(Scanner scanner, String message) {
        while (true) {
            System.out.print(message);
            try {
                return new BigDecimal(scanner.nextLine());
            } catch (NumberFormatException exception) {
                System.out.println("Enter a valid amount.");
            }
        }
    }
}
