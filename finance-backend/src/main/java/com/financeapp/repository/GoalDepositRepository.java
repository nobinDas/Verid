package com.financeapp.repository;

import com.financeapp.model.GoalDeposit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface GoalDepositRepository extends JpaRepository<GoalDeposit, Long> {

    boolean existsByGoalIdAndUserIdAndMonthAndYear(Long goalId, Long userId, short month, short year);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM GoalDeposit d WHERE d.userId = :userId")
    BigDecimal sumAmountByUserId(@Param("userId") Long userId);
}
