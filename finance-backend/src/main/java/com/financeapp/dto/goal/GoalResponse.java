package com.financeapp.dto.goal;

import com.financeapp.model.Goal;
import com.financeapp.model.Goal.GoalStatus;
import com.financeapp.model.Goal.TermType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record GoalResponse(
        Long id,
        String title,
        String description,
        BigDecimal targetAmount,
        BigDecimal currentAmount,
        BigDecimal progressPercent,
        TermType termType,
        LocalDate deadline,
        GoalStatus status,
        BigDecimal allocationPercent,
        LocalDateTime createdAt,
        boolean depositedThisMonth
) {
    public static GoalResponse from(Goal goal, boolean depositedThisMonth) {
        BigDecimal progress = BigDecimal.ZERO;
        if (goal.getTargetAmount() != null && goal.getTargetAmount().compareTo(BigDecimal.ZERO) > 0) {
            progress = goal.getCurrentAmount()
                    .multiply(new BigDecimal("100"))
                    .divide(goal.getTargetAmount(), 2, RoundingMode.HALF_UP);
        }
        return new GoalResponse(
                goal.getId(),
                goal.getTitle(),
                goal.getDescription(),
                goal.getTargetAmount(),
                goal.getCurrentAmount(),
                progress,
                goal.getTermType(),
                goal.getDeadline(),
                goal.getStatus(),
                goal.getAllocationPercent(),
                goal.getCreatedAt(),
                depositedThisMonth
        );
    }

    public static GoalResponse from(Goal goal) {
        return from(goal, false);
    }
}
