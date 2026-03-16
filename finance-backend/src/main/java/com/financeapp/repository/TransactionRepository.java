package com.financeapp.repository;

import com.financeapp.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByStatementIdOrderByDateDesc(Long statementId);
    long countByStatementId(Long statementId);
    List<Transaction> findTop30ByUserIdOrderByDateDesc(Long userId);
    List<Transaction> findByUserIdOrderByDateDesc(Long userId);
    List<Transaction> findByUserIdAndDateBetween(Long userId, LocalDate from, LocalDate to);
    List<Transaction> findByUserIdAndClassificationIn(Long userId, List<String> classifications);
}
