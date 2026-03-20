package com.financeapp.service.parser;

import com.financeapp.model.Transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CapitalOneParser {

    private static final Pattern ACCOUNT_PATTERN =
            Pattern.compile("ending in (\\d{4})");

    // Matches the USD amount line that follows foreign-currency exchange rate lines
    private static final Pattern USD_LINE_PATTERN = Pattern.compile("\\$([\\d,]+\\.\\d{2})");

    private static final Pattern PERIOD_PATTERN =
            Pattern.compile("(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+(\\d{1,2}),\\s+(\\d{4})\\s*-\\s*(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+(\\d{1,2}),\\s+(\\d{4})");

    private static final Pattern TX_PATTERN =
            Pattern.compile("^(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+(\\d{1,2})\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+(\\d{1,2})\\s+(.+?)\\s+(-\\s*\\$[\\d,]+\\.\\d{2}|\\$[\\d,]+\\.\\d{2})\\s*$");

    public ParsedStatement parse(String text) {
        // Extract account last4
        Matcher accountMatcher = ACCOUNT_PATTERN.matcher(text);
        if (!accountMatcher.find()) {
            throw new IllegalArgumentException("Could not parse Capital One statement: account number not found");
        }
        String accountLast4 = accountMatcher.group(1);

        // Extract period
        Matcher periodMatcher = PERIOD_PATTERN.matcher(text);
        if (!periodMatcher.find()) {
            throw new IllegalArgumentException("Could not parse Capital One statement: billing period not found");
        }
        LocalDate periodStart = LocalDate.of(
                Integer.parseInt(periodMatcher.group(3)),
                monthToInt(periodMatcher.group(1)),
                Integer.parseInt(periodMatcher.group(2)));
        LocalDate periodEnd = LocalDate.of(
                Integer.parseInt(periodMatcher.group(6)),
                monthToInt(periodMatcher.group(4)),
                Integer.parseInt(periodMatcher.group(5)));

        // Parse transactions
        List<ParsedTransaction> transactions = new ArrayList<>();
        // Track section: null = unknown, CREDIT = payments/credits, DEBIT = purchases
        TransactionType currentSection = null;

        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();

            // Section detection — headers may be prefixed with cardholder name,
            // e.g. "NOBIN DAS NIRJHAR #4488: Payments, Credits and Adjustments"
            if (trimmed.contains("Payments, Credits and Adjustments")) {
                currentSection = TransactionType.CREDIT;
                continue;
            }
            // Match "Transactions" section but exclude "Total Transactions" and header row
            if (trimmed.contains("Transactions") && !trimmed.contains("Total") && !trimmed.contains("Trans Date")) {
                currentSection = TransactionType.DEBIT;
                continue;
            }

            // Skip header row
            if (trimmed.startsWith("Trans Date")) {
                continue;
            }

            if (currentSection == null) {
                continue;
            }

            Matcher txMatcher = TX_PATTERN.matcher(trimmed);
            if (!txMatcher.matches()) {
                continue;
            }

            String txMonthStr = txMatcher.group(1);
            int txDay = Integer.parseInt(txMatcher.group(2));
            // groups 3+4 are post date — ignored for date resolution
            String description = txMatcher.group(5).trim();
            String amountStr = txMatcher.group(6);

            int txMonth = monthToInt(txMonthStr);
            int txYear;
            if (txMonth == periodStart.getMonthValue()) {
                txYear = periodStart.getYear();
            } else if (txMonth == periodEnd.getMonthValue()) {
                txYear = periodEnd.getYear();
            } else {
                txYear = periodStart.getYear();
            }

            LocalDate txDate = LocalDate.of(txYear, txMonth, txDay);

            boolean isNegative = amountStr.contains("-");
            String cleaned = amountStr.replaceAll("[-$,\\s]", "");
            BigDecimal amount = new BigDecimal(cleaned);

            // Foreign currency lookahead: Capital One shows foreign transactions as:
            //   [TX line with foreign amount]
            //   [3-letter currency code, e.g. BDT]
            //   [rate] Exchange Rate
            //   $[USD amount]
            // When detected, replace amount with the actual USD charged amount.
            if (i + 3 < lines.length) {
                String currencyLine = lines[i + 1].trim();
                String rateLine    = lines[i + 2].trim();
                String usdLine     = lines[i + 3].trim();
                if (currencyLine.matches("[A-Z]{3}") && rateLine.contains("Exchange Rate")) {
                    Matcher usdMatcher = USD_LINE_PATTERN.matcher(usdLine);
                    if (usdMatcher.find()) {
                        amount = new BigDecimal(usdMatcher.group(1).replace(",", ""));
                        i += 3; // skip the 3 foreign-currency detail lines
                    }
                }
            }

            // Amount sign: negative → CREDIT (payment/refund), positive → DEBIT (charge).
            TransactionType type = isNegative ? TransactionType.CREDIT : TransactionType.DEBIT;

            transactions.add(new ParsedTransaction(txDate, description, amount, type));
        }

        return new ParsedStatement("CAPITAL_ONE", accountLast4, "CREDIT", periodStart, periodEnd, transactions);
    }

    private static int monthToInt(String month) {
        return switch (month) {
            case "Jan" -> 1;
            case "Feb" -> 2;
            case "Mar" -> 3;
            case "Apr" -> 4;
            case "May" -> 5;
            case "Jun" -> 6;
            case "Jul" -> 7;
            case "Aug" -> 8;
            case "Sep" -> 9;
            case "Oct" -> 10;
            case "Nov" -> 11;
            case "Dec" -> 12;
            default -> throw new IllegalArgumentException("Unknown month: " + month);
        };
    }
}
