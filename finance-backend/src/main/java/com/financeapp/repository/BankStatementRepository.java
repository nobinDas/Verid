package com.financeapp.repository;

import com.financeapp.model.BankStatement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankStatementRepository extends JpaRepository<BankStatement, Long> {
    List<BankStatement> findByUserIdOrderByYearDescMonthDesc(Long userId);
    Optional<BankStatement> findByIdAndUserId(Long id, Long userId);
    boolean existsByUserIdAndBankNameAndAccountLast4AndMonthAndYear(Long userId, String bankName, String accountLast4, Short month, Short year);
    Optional<BankStatement> findByUserIdAndFileHash(Long userId, String fileHash);
}
