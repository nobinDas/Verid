package com.financeapp.dto.investment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestmentRequest(
        @NotBlank String platform,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull LocalDate investedAt,
        String recipient,
        String contactInfo,
        LocalDate expectedReturnDate,
        BigDecimal interestRate,
        String notes
) {}
