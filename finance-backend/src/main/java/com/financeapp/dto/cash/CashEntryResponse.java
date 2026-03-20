package com.financeapp.dto.cash;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CashEntryResponse(
    Long id,
    LocalDate date,
    String description,
    BigDecimal amount,
    String type,
    String category,
    LocalDateTime createdAt
) {}
