package com.financeapp.dto.goal;

import com.financeapp.model.Goal.TermType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateGoalRequest(
        @NotBlank String title,
        String description,
        @NotNull BigDecimal targetAmount,
        @NotNull TermType termType,
        LocalDate deadline,
        BigDecimal allocationPercent
) {}
