package com.financeapp.dto.goal;

import com.financeapp.model.Goal.GoalStatus;
import com.financeapp.model.Goal.TermType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateGoalRequest(
        String title,
        String description,
        BigDecimal targetAmount,
        TermType termType,
        LocalDate deadline,
        BigDecimal allocationPercent,
        GoalStatus status
) {}
