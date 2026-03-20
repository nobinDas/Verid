package com.financeapp.dto.cash;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashEntryRequest(
    LocalDate date,
    String description,
    BigDecimal amount,
    String type,      // "IN" or "OUT"
    String category
) {}
