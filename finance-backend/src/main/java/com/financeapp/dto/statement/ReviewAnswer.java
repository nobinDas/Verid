package com.financeapp.dto.statement;

import java.util.List;

public record ReviewAnswer(
    List<Long> transactionIds,
    Boolean isIncome
) {}
