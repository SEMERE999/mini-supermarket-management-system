package com.minisupermarket.ui;

import java.math.BigDecimal;
import java.util.Scanner;

public class InputReader {
    private final Scanner scanner = new Scanner(System.in);

    public String promptString(String message) {
        System.out.print(message);
        return scanner.nextLine().trim();
    }

    public String promptOptionalString(String message) {
        System.out.print(message);
        String value = scanner.nextLine().trim();
        return value.isBlank() ? null : value;
    }

    public int promptInt(String message) {
        while (true) {
            try {
                return Integer.parseInt(promptString(message));
            } catch (NumberFormatException ex) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    public Integer promptOptionalInt(String message) {
        while (true) {
            String rawValue = promptOptionalString(message);
            if (rawValue == null) {
                return null;
            }
            try {
                return Integer.parseInt(rawValue);
            } catch (NumberFormatException ex) {
                System.out.println("Please enter a valid number or leave it blank.");
            }
        }
    }

    public BigDecimal promptBigDecimal(String message) {
        while (true) {
            try {
                return new BigDecimal(promptString(message));
            } catch (NumberFormatException ex) {
                System.out.println("Please enter a valid amount.");
            }
        }
    }
}
