package com.financeapp.repository;

import com.financeapp.model.StatementUploadHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StatementUploadHistoryRepository extends JpaRepository<StatementUploadHistory, Long> {

    Optional<StatementUploadHistory> findByUserIdAndFileHash(Long userId, String fileHash);
}
