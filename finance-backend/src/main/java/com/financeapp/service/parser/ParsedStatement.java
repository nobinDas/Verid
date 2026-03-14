package com.financeapp.service.parser;

import java.time.LocalDate;
import java.util.List;

public record ParsedStatement(
    String bankName,
    String accountLast4,
    String accountType,
    LocalDate periodStart,
    LocalDate periodEnd,
    List<ParsedTransaction> transactions
) {}
