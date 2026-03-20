package com.financeapp.dto.statement;

import java.time.LocalDateTime;
import java.util.List;

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
    LocalDateTime uploadDate,
    List<PendingReviewItem> pendingReviews
) {
    // Convenience constructor for non-processing responses (no pending reviews)
    public StatementResponse(Long id, String filename, String bankName, String accountLast4,
                              String accountType, int month, int year, boolean processed,
                              long transactionCount, LocalDateTime uploadDate) {
        this(id, filename, bankName, accountLast4, accountType, month, year, processed,
             transactionCount, uploadDate, List.of());
    }
}
