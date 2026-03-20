package com.financeapp.util;

import com.financeapp.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class TestFactory {

    public static User buildUser(Long id, String email) {
        return User.builder()
                .id(id)
                .email(email)
                .name("Test User")
                .passwordHash("$2a$10$dummy.hash.for.testing.purposes.only")
                .currency("USD")
                .createdAt(LocalDateTime.now())
                .build();
    }

    public static Goal buildGoal(Long id, Long userId) {
        return Goal.builder()
                .id(id)
                .userId(userId)
                .title("Emergency Fund")
                .description("3 months of expenses")
                .targetAmount(new BigDecimal("5000.00"))
                .currentAmount(BigDecimal.ZERO)
                .termType(Goal.TermType.MID)
                .status(Goal.GoalStatus.ACTIVE)
                .allocationPercent(new BigDecimal("20.00"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    public static Transaction buildTransaction(Long userId, Long statementId,
                                               Transaction.TransactionType type,
                                               BigDecimal amount, String classification) {
        return Transaction.builder()
                .id(null)
                .userId(userId)
                .statementId(statementId)
                .date(LocalDate.now())
                .description("Test transaction")
                .amount(amount)
                .type(type)
                .classification(classification)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public static Transaction buildTransactionOn(Long userId, Long statementId,
                                                 Transaction.TransactionType type,
                                                 BigDecimal amount, String classification,
                                                 LocalDate date) {
        return Transaction.builder()
                .id(null)
                .userId(userId)
                .statementId(statementId)
                .date(date)
                .description("Test transaction " + date)
                .amount(amount)
                .type(type)
                .classification(classification)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public static CashEntry buildCashEntry(Long userId, String type, BigDecimal amount) {
        return CashEntry.builder()
                .id(null)
                .userId(userId)
                .date(LocalDate.now())
                .description("Test cash entry")
                .amount(amount)
                .type(type)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public static IncomeRule buildIncomeRule(Long userId, String pattern, boolean isIncome) {
        return IncomeRule.builder()
                .userId(userId)
                .pattern(pattern)
                .income(isIncome)
                .seenCount(1)
                .lastSeen(LocalDate.now())
                .createdAt(LocalDateTime.now())
                .build();
    }
}
