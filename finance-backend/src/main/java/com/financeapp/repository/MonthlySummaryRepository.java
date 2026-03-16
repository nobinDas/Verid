package com.financeapp.repository;

import com.financeapp.model.MonthlySummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonthlySummaryRepository extends JpaRepository<MonthlySummary, Long> {
    List<MonthlySummary> findByUserIdOrderByYearDescMonthDesc(Long userId);
    Optional<MonthlySummary> findByUserIdAndMonthAndYear(Long userId, Short month, Short year);
}
