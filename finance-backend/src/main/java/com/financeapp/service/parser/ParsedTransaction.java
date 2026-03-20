package com.financeapp.service.parser;

import com.financeapp.model.Transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParsedTransaction(LocalDate date, String description, BigDecimal amount, TransactionType type) {}
