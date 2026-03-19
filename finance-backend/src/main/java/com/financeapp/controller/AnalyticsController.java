package com.financeapp.controller;

import com.financeapp.model.CashEntry;
import com.financeapp.model.Transaction;
import com.financeapp.model.User;
import com.financeapp.repository.CashEntryRepository;
import com.financeapp.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private static final Set<String> INCOME_CLASSIFICATIONS  = Set.of("INCOME", "EXTRA_IN", "CASH_IN");
    private static final Set<String> EXPENSE_CLASSIFICATIONS = Set.of("EXPENSE", "CASH_OUT", "CC_CHARGE");

    private final TransactionRepository transactionRepository;
    private final CashEntryRepository   cashEntryRepository;

    record CategoryTotal(String category, BigDecimal total) {}
    record TopTransaction(LocalDate date, String description, BigDecimal amount, String classification, String category) {}

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryTotal>> getCategories(
            @RequestParam String from,
            @RequestParam String to) {

        Long userId = currentUserId();
        LocalDate fromDate = YearMonth.parse(from).atDay(1);
        LocalDate toDate   = YearMonth.parse(to).atEndOfMonth();

        List<Transaction> transactions = transactionRepository.findByUserIdAndDateBetween(userId, fromDate, toDate);
        Map<String, BigDecimal> totals = new LinkedHashMap<>();

        for (Transaction t : transactions) {
            if (!EXPENSE_CLASSIFICATIONS.contains(t.getClassification())) continue;
            String cat = t.getCategory() != null ? t.getCategory() : "Uncategorized";
            totals.merge(cat, t.getAmount(), BigDecimal::add);
        }

        // Cash entries (OUT type)
        List<CashEntry> cashEntries = cashEntryRepository.findByUserIdOrderByDateDesc(userId);
        for (CashEntry c : cashEntries) {
            if (!"OUT".equals(c.getType())) continue;
            if (c.getDate().isBefore(fromDate) || c.getDate().isAfter(toDate)) continue;
            String cat = c.getCategory() != null ? c.getCategory() : "Uncategorized";
            totals.merge(cat, c.getAmount(), BigDecimal::add);
        }

        List<CategoryTotal> result = totals.entrySet().stream()
                .map(e -> new CategoryTotal(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(CategoryTotal::total).reversed())
                .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/top-transactions")
    public ResponseEntity<List<TopTransaction>> getTopTransactions(
            @RequestParam String from,
            @RequestParam String to) {

        Long userId = currentUserId();
        LocalDate fromDate = YearMonth.parse(from).atDay(1);
        LocalDate toDate   = YearMonth.parse(to).atEndOfMonth();

        List<Transaction> transactions = transactionRepository.findByUserIdAndDateBetween(userId, fromDate, toDate);
        List<TopTransaction> txList = transactions.stream()
                .filter(t -> INCOME_CLASSIFICATIONS.contains(t.getClassification())
                          || EXPENSE_CLASSIFICATIONS.contains(t.getClassification()))
                .map(t -> new TopTransaction(
                        t.getDate(), t.getDescription(), t.getAmount(),
                        t.getClassification(), t.getCategory()))
                .collect(Collectors.toList());

        // Cash entries
        List<CashEntry> cashEntries = cashEntryRepository.findByUserIdOrderByDateDesc(userId);
        for (CashEntry c : cashEntries) {
            if (c.getDate().isBefore(fromDate) || c.getDate().isAfter(toDate)) continue;
            String cls = "IN".equals(c.getType()) ? "CASH_IN" : "CASH_OUT";
            txList.add(new TopTransaction(
                    c.getDate(), c.getDescription(), c.getAmount(), cls, c.getCategory()));
        }

        List<TopTransaction> result = txList.stream()
                .sorted(Comparator.comparing(TopTransaction::amount).reversed())
                .toList();

        return ResponseEntity.ok(result);
    }

    private Long currentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
