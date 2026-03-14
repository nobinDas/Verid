package com.financeapp.dto.statement;

import java.time.LocalDateTime;

public record StatementResponse(
    Long id,
    String filename,
    String bankName,
    String accountLast4,
    String accountType,
    int month,
    int year,
    boolean processed,
    long transactionCount,
    LocalDateTime uploadDate
) {}
