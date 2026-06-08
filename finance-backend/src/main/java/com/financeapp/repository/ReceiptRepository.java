package com.financeapp.repository;

import com.financeapp.model.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    List<Receipt> findByUserIdAndExpiresAtAfterOrderByPurchaseDateDesc(Long userId, LocalDate now);

    Optional<Receipt> findByIdAndUserId(Long id, Long userId);

    List<Receipt> findByExpiresAtBefore(LocalDate now);
}
