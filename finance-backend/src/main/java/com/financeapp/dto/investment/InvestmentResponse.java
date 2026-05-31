package com.financeapp.dto.investment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InvestmentResponse(
        Long id,
        String source,
        String platform,
        BigDecimal amount,
        LocalDate investedAt,
        Long transactionId,
        String recipient,
        String contactInfo,
        LocalDate expectedReturnDate,
        BigDecimal interestRate,
        String notes,
        LocalDateTime createdAt,
        Long daysUntilReturn,
        BigDecimal expectedReturn
) {}
