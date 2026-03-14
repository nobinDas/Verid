package com.financeapp.service.parser;

import com.financeapp.model.Transaction.TransactionType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegionsParser {

    private static final Logger log = LoggerFactory.getLogger(RegionsParser.class);

    // Case-insensitive; handles "ACCOUNT # 0329073677" and "Account# 0329073677"
    private static final Pattern ACCOUNT_PATTERN =
            Pattern.compile("(?i)account\\s*#\\s*(\\d+)");

    // Case-insensitive; handles slight spacing differences around "through"
    private static final Pattern PERIOD_PATTERN =
            Pattern.compile(
                "(?i)(january|february|march|april|may|june|july|august|september|october|november|december)" +
                "\\s+(\\d{1,2}),?\\s+(\\d{4})\\s+through\\s+" +
                "(january|february|march|april|may|june|july|august|september|october|november|december)" +
                "\\s+(\\d{1,2}),?\\s+(\\d{4})");

    // Flat row: MM/dd  <description blob>  <amount>
    private static final Pattern TX_PATTERN =
            Pattern.compile("^(\\d{2}/\\d{2})\\s+(.+?)\\s+([\\d,]+\\.\\d{2})\\s*$");

    // Section headers — case-insensitive contains check done in loop
    private static final String[] CREDIT_HEADERS  = {"deposits & credits", "deposits and credits"};
    private static final String[] DEBIT_HEADERS   = {"withdrawals", "fees"};
    // Lines that mark the end of transaction data — stop parsing when seen.
    // "total deposits" and "total withdrawals" are intentionally omitted: they
    // don't match TX_PATTERN (no MM/DD prefix) so they're harmlessly skipped,
    // and stopping on them would prevent parsing the next section.
    private static final String[] STOP_KEYWORDS   = {"daily balance", "easy steps to balance"};

    public ParsedStatement parse(String text) {
        // ── Account type — inspect only the first 30 lines (header section)
        // so transaction descriptions like "EB From Savings #..." don't skew detection
        String[] allLines = text.split("\\r?\\n");
        int headerEnd = Math.min(20, allLines.length);
        StringBuilder headerText = new StringBuilder();
        for (int i = 0; i < headerEnd; i++) headerText.append(allLines[i]).append('\n');
        String headerLower = headerText.toString().toLowerCase();

        String accountType;
        if (headerLower.contains("savings") || headerLower.contains("money market")) {
            accountType = "SAVINGS";
        } else {
            accountType = "CHECKING";
        }

        // ── Account last 4 ────────────────────────────────────────────────────
        log.info("Header text (first 30 lines):\n{}", headerText);
        log.info("Account type detected: {}", accountType);

        Matcher accountMatcher = ACCOUNT_PATTERN.matcher(text);
        if (!accountMatcher.find()) {
            log.error("ACCOUNT_PATTERN failed. First 500 chars of text:\n{}", text.substring(0, Math.min(500, text.length())));
            throw new IllegalArgumentException(
                "Could not parse Regions statement: account number not found. " +
                "Expected a line like 'ACCOUNT # 0329073677'.");
        }
        String fullAccountNumber = accountMatcher.group(1);
        String accountLast4 = fullAccountNumber.length() >= 4
                ? fullAccountNumber.substring(fullAccountNumber.length() - 4)
                : fullAccountNumber;

        // ── Statement period ──────────────────────────────────────────────────
        log.info("Account last4: {}", accountLast4);

        Matcher periodMatcher = PERIOD_PATTERN.matcher(text);
        if (!periodMatcher.find()) {
            log.error("PERIOD_PATTERN failed. Searching in text for 'through'... found: {}", text.toLowerCase().contains("through"));
            throw new IllegalArgumentException(
                "Could not parse Regions statement: period not found. " +
                "Expected format like 'January 27, 2026 through February 23, 2026'.");
        }
        LocalDate periodStart = LocalDate.of(
                Integer.parseInt(periodMatcher.group(3)),
                monthToInt(periodMatcher.group(1).toLowerCase()),
                Integer.parseInt(periodMatcher.group(2)));
        LocalDate periodEnd = LocalDate.of(
                Integer.parseInt(periodMatcher.group(6)),
                monthToInt(periodMatcher.group(4).toLowerCase()),
                Integer.parseInt(periodMatcher.group(5)));

        // ── Transactions ──────────────────────────────────────────────────────
        List<ParsedTransaction> transactions = new ArrayList<>();
        TransactionType currentSection = null;

        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            String trimmedLower = trimmed.toLowerCase();

            // Stop keywords — past transaction data, stop parsing
            boolean shouldStop = false;
            for (String s : STOP_KEYWORDS) { if (trimmedLower.contains(s)) { shouldStop = true; break; } }
            if (shouldStop) break;

            // Section header detection
            boolean isCredit = false;
            for (String h : CREDIT_HEADERS) { if (trimmedLower.contains(h)) { isCredit = true; break; } }
            if (isCredit) { currentSection = TransactionType.CREDIT; continue; }

            boolean isDebit = false;
            for (String h : DEBIT_HEADERS) { if (trimmedLower.contains(h)) { isDebit = true; break; } }
            if (isDebit) { currentSection = TransactionType.DEBIT; continue; }

            if (currentSection == null) continue;

            Matcher txMatcher = TX_PATTERN.matcher(trimmed);
            if (!txMatcher.matches()) continue;

            String dateStr   = txMatcher.group(1);
            String desc      = txMatcher.group(2).trim();
            String amountStr = txMatcher.group(3);

            String[] dateParts = dateStr.split("/");
            int txMonth = Integer.parseInt(dateParts[0]);
            int txDay   = Integer.parseInt(dateParts[1]);

            int txYear;
            if (txMonth == periodStart.getMonthValue())     txYear = periodStart.getYear();
            else if (txMonth == periodEnd.getMonthValue())  txYear = periodEnd.getYear();
            else                                            txYear = periodStart.getYear();

            LocalDate txDate = LocalDate.of(txYear, txMonth, txDay);
            BigDecimal amount = new BigDecimal(amountStr.replace(",", ""));

            transactions.add(new ParsedTransaction(txDate, desc, amount, currentSection));
        }

        return new ParsedStatement("REGIONS", accountLast4, accountType, periodStart, periodEnd, transactions);
    }

    private static int monthToInt(String month) {
        return switch (month) {
            case "january"   -> 1;
            case "february"  -> 2;
            case "march"     -> 3;
            case "april"     -> 4;
            case "may"       -> 5;
            case "june"      -> 6;
            case "july"      -> 7;
            case "august"    -> 8;
            case "september" -> 9;
            case "october"   -> 10;
            case "november"  -> 11;
            case "december"  -> 12;
            default -> throw new IllegalArgumentException("Unknown month: " + month);
        };
    }
}
