package com.financeapp.repository;

import com.financeapp.model.IncomeRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IncomeRuleRepository extends JpaRepository<IncomeRule, Long> {
    Optional<IncomeRule> findByUserIdAndPattern(Long userId, String pattern);
}
