package com.financeapp.repository;

import com.financeapp.model.CashEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashEntryRepository extends JpaRepository<CashEntry, Long> {
    List<CashEntry> findByUserIdOrderByDateDesc(Long userId);
}
