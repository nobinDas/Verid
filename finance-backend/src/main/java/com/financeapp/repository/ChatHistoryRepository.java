package com.financeapp.repository;

import com.financeapp.model.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {
    List<ChatHistory> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
