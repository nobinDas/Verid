package com.financeapp.dto.statement;

import java.math.BigDecimal;
import java.util.List;

public record PendingReviewItem(
    List<Long> transactionIds,  // all tx IDs from this source in this statement
    String description,          // representative description (first occurrence)
    int count,                   // how many transactions share this source
    BigDecimal totalAmount       // sum of all amounts in the group
) {}
