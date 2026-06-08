package com.financeapp.dto.receipt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReceiptResponse(
        Long id,
        String storeName,
        LocalDate purchaseDate,
        BigDecimal totalAmount,
        String itemsJson,
        String originalFilename,
        Integer retentionYears,
        LocalDate expiresAt,
        LocalDateTime createdAt
) {}
