package com.financeapp.dto.statement;

import java.math.BigDecimal;
import java.util.List;

public record PendingSheetItem(
    List<Long> transactionIds,
    String description,
    String aiCategory,
    BigDecimal totalAmount
) {}
