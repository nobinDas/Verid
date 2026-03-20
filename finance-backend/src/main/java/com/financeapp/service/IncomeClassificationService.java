package com.financeapp.service;

import com.financeapp.model.IncomeRule;
import com.financeapp.repository.IncomeRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IncomeClassificationService {

    private final IncomeRuleRepository incomeRuleRepository;

    /**
     * Classifies a CREDIT transaction on a checking/savings account.
     *
     * @return "INCOME", "EXTRA_IN", or "UNCLASSIFIED"
     */
    public String classify(Long userId, String description, LocalDate date) {
        String pattern = normalize(description);
        Optional<IncomeRule> existing = incomeRuleRepository.findByUserIdAndPattern(userId, pattern);

        if (existing.isPresent()) {
            IncomeRule rule = existing.get();
            rule.setSeenCount(rule.getSeenCount() + 1);
            rule.setLastSeen(date);

            // If marked as non-income but seen 3+ times, ask user again
            if (!rule.isIncome() && rule.getSeenCount() >= 3) {
                rule.setSeenCount(0);
                incomeRuleRepository.save(rule);
                return "UNCLASSIFIED";
            }

            incomeRuleRepository.save(rule);
            return rule.isIncome() ? "INCOME" : "EXTRA_IN";
        }

        // New source — flag for user review
        return "UNCLASSIFIED";
    }

    /**
     * Saves or updates an income rule based on user's review answer.
     */
    public void saveRule(Long userId, String description, boolean isIncome, LocalDate date) {
        String pattern = normalize(description);
        IncomeRule rule = incomeRuleRepository.findByUserIdAndPattern(userId, pattern)
                .orElseGet(() -> IncomeRule.builder()
                        .userId(userId)
                        .pattern(pattern)
                        .build());

        rule.setIncome(isIncome);
        rule.setSeenCount(1);
        rule.setLastSeen(date);
        incomeRuleRepository.save(rule);
    }

    public static String normalize(String description) {
        if (description == null) return "";
        return Arrays.stream(
                description
                        .replaceAll("(?i)\\s*\\bref#?\\s+.*", "") // strip "Ref# <id>" and everything after
                        .toLowerCase()
                        .replaceAll("[^a-z\\s]", "")              // strip remaining digits, punctuation
                        .replaceAll("\\s+", " ")
                        .trim()
                        .split(" "))
                .filter(token -> token.length() > 1)              // drop single stray chars
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
