package com.financeapp.dto.statement;

import java.util.List;

public record SheetMappingAnswer(
    List<Long> transactionIds,
    String section,
    String categoryName
) {}
