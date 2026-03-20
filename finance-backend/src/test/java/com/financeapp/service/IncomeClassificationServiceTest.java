package com.financeapp.service;

import com.financeapp.model.IncomeRule;
import com.financeapp.repository.IncomeRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncomeClassificationServiceTest {

    @Mock IncomeRuleRepository incomeRuleRepository;

    @InjectMocks
    IncomeClassificationService service;

    private static final Long USER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.now();

    // ── classify ──────────────────────────────────────────────────────────────

    @Test
    void classify_knownIncomeRule_returnsIncome() {
        String pattern = IncomeClassificationService.normalize("Company Payroll");
        IncomeRule rule = rule(USER_ID, pattern, true, 5);
        when(incomeRuleRepository.findByUserIdAndPattern(USER_ID, pattern)).thenReturn(Optional.of(rule));

        String result = service.classify(USER_ID, "Company Payroll", TODAY);

        assertThat(result).isEqualTo("INCOME");
    }

    @Test
    void classify_knownNonIncomeRule_returnsExtraIn() {
        String pattern = IncomeClassificationService.normalize("Friend Payment");
        IncomeRule rule = rule(USER_ID, pattern, false, 1);
        when(incomeRuleRepository.findByUserIdAndPattern(USER_ID, pattern)).thenReturn(Optional.of(rule));

        String result = service.classify(USER_ID, "Friend Payment", TODAY);

        assertThat(result).isEqualTo("EXTRA_IN");
    }

    @Test
    void classify_nonIncomeSeenThreeTimes_resetsAndReturnsUnclassified() {
        String pattern = IncomeClassificationService.normalize("Mystery Credit");
        // seenCount = 3, non-income → triggers re-review
        IncomeRule rule = rule(USER_ID, pattern, false, 3);
        when(incomeRuleRepository.findByUserIdAndPattern(USER_ID, pattern)).thenReturn(Optional.of(rule));
        when(incomeRuleRepository.save(any())).thenReturn(rule);

        String result = service.classify(USER_ID, "Mystery Credit", TODAY);

        assertThat(result).isEqualTo("UNCLASSIFIED");
        assertThat(rule.getSeenCount()).isEqualTo(0); // reset
    }

    @Test
    void classify_noRule_returnsUnclassified() {
        when(incomeRuleRepository.findByUserIdAndPattern(any(), any())).thenReturn(Optional.empty());

        String result = service.classify(USER_ID, "Unknown Source", TODAY);

        assertThat(result).isEqualTo("UNCLASSIFIED");
    }

    // ── saveRule ──────────────────────────────────────────────────────────────

    @Test
    void saveRule_newPattern_persistsRule() {
        when(incomeRuleRepository.findByUserIdAndPattern(any(), any())).thenReturn(Optional.empty());
        when(incomeRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.saveRule(USER_ID, "Employer Inc", true, TODAY);

        verify(incomeRuleRepository).save(argThat(r ->
                r.isIncome() && r.getPattern().equals(service.normalize("Employer Inc"))
        ));
    }

    @Test
    void saveRule_existingPattern_updatesRule() {
        String pattern = IncomeClassificationService.normalize("Freelance Work");
        IncomeRule existing = rule(USER_ID, pattern, false, 2);
        when(incomeRuleRepository.findByUserIdAndPattern(USER_ID, pattern)).thenReturn(Optional.of(existing));
        when(incomeRuleRepository.save(any())).thenReturn(existing);

        service.saveRule(USER_ID, "Freelance Work", true, TODAY);

        assertThat(existing.isIncome()).isTrue();
        verify(incomeRuleRepository).save(existing);
    }

    // ── normalize ─────────────────────────────────────────────────────────────

    @Test
    void normalize_stripsNumbersAndPunctuation() {
        String result = IncomeClassificationService.normalize("Amazon.com Order 1234");
        // digits and punctuation stripped, single chars dropped
        assertThat(result).doesNotContain("1234");
        assertThat(result).doesNotContain(".");
    }

    @Test
    void normalize_stripsRefNumber() {
        String result = IncomeClassificationService.normalize("Payroll REF# 987654321");
        assertThat(result).doesNotContain("987654321");
    }

    @Test
    void normalize_nullDescription_returnsEmpty() {
        assertThat(IncomeClassificationService.normalize(null)).isEmpty();
    }

    @Test
    void normalize_dropsSingleCharTokens() {
        String result = IncomeClassificationService.normalize("A Big Store");
        // "A" is a single char → dropped
        assertThat(result).doesNotContain(" a ").doesNotStartWith("a ");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private IncomeRule rule(Long userId, String pattern, boolean isIncome, int seenCount) {
        return IncomeRule.builder()
                .userId(userId).pattern(pattern).income(isIncome)
                .seenCount(seenCount).lastSeen(TODAY).createdAt(LocalDateTime.now()).build();
    }
}
