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
    List<PendingReviewItem> pendingReviews,
    List<PendingSheetItem> pendingSheetMappings
) {
    // No reviews or sheet mappings (list responses, delete, etc.)
    public StatementResponse(Long id, String filename, String bankName, String accountLast4,
                              String accountType, int month, int year, boolean processed,
                              long transactionCount, LocalDateTime uploadDate) {
        this(id, filename, bankName, accountLast4, accountType, month, year, processed,
             transactionCount, uploadDate, List.of(), List.of());
    }

    // With income reviews only (no sheet mappings)
    public StatementResponse(Long id, String filename, String bankName, String accountLast4,
                              String accountType, int month, int year, boolean processed,
                              long transactionCount, LocalDateTime uploadDate,
                              List<PendingReviewItem> pendingReviews) {
        this(id, filename, bankName, accountLast4, accountType, month, year, processed,
             transactionCount, uploadDate, pendingReviews, List.of());
    }
}
