package com.financeapp.controller;

import com.financeapp.model.CashEntry;
import com.financeapp.model.Transaction;
import com.financeapp.model.User;
import com.financeapp.repository.CashEntryRepository;
import com.financeapp.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/summaries")
@RequiredArgsConstructor
public class SummaryController {

    private static final Set<String> INCOME_CLASSIFICATIONS   = Set.of("INCOME", "CASH_IN");
    private static final Set<String> EXPENSE_CLASSIFICATIONS  = Set.of("EXPENSE", "CASH_OUT");

    private final TransactionRepository transactionRepository;
    private final CashEntryRepository cashEntryRepository;

    record MonthKey(int year, int month) {}
    record SavingsPoint(int month, int year, BigDecimal totalIncome, BigDecimal totalExpenses, BigDecimal netSavings, BigDecimal totalExtraIn) {}

    @GetMapping
    public ResponseEntity<List<SavingsPoint>> getSavings() {
        Long userId = currentUserId();

        List<Transaction> allTx = transactionRepository.findByUserIdOrderByDateDesc(userId);
        List<CashEntry> cashEntries = cashEntryRepository.findByUserIdOrderByDateDesc(userId);

        // index: 0=income, 1=expenses, 2=extraIn
        Map<MonthKey, BigDecimal[]> grouped = new LinkedHashMap<>();

        for (Transaction t : allTx) {
            String cls = t.getClassification();
            if (!INCOME_CLASSIFICATIONS.contains(cls) && !EXPENSE_CLASSIFICATIONS.contains(cls)
                    && !"EXTRA_IN".equals(cls) && !"CC_CHARGE".equals(cls)) continue;

            MonthKey key = new MonthKey(t.getDate().getYear(), t.getDate().getMonthValue());
            BigDecimal[] sums = grouped.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            if (INCOME_CLASSIFICATIONS.contains(cls)) {
                sums[0] = sums[0].add(t.getAmount());
            } else if (EXPENSE_CLASSIFICATIONS.contains(cls)) {
                sums[1] = sums[1].add(t.getAmount());
            } else if ("EXTRA_IN".equals(cls)) {
                sums[2] = sums[2].add(t.getAmount());
            }
            // CC_CHARGE: registers the month in the picker without affecting income/expense totals
        }

        for (CashEntry c : cashEntries) {
            MonthKey key = new MonthKey(c.getDate().getYear(), c.getDate().getMonthValue());
            BigDecimal[] sums = grouped.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            if ("IN".equals(c.getType())) {
                sums[0] = sums[0].add(c.getAmount());
            } else {
                sums[1] = sums[1].add(c.getAmount());
            }
        }

        List<SavingsPoint> result = grouped.entrySet().stream()
                .map(e -> {
                    BigDecimal income   = e.getValue()[0];
                    BigDecimal expenses = e.getValue()[1];
                    BigDecimal extraIn  = e.getValue()[2];
                    return new SavingsPoint(
                            e.getKey().month(), e.getKey().year(),
                            income, expenses, income.subtract(expenses), extraIn);
                })
                .sorted(Comparator.comparingInt((SavingsPoint s) -> s.year() * 100 + s.month()).reversed())
                .toList();

        return ResponseEntity.ok(result);
    }

    private Long currentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
