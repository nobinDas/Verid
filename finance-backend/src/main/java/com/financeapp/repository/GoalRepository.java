package com.financeapp.repository;

import com.financeapp.model.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Goal> findByIdAndUserId(Long id, Long userId);
}
