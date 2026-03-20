package com.financeapp.service;

import com.financeapp.model.Transaction;
import com.financeapp.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferDetectionServiceTest {

    @Mock TransactionRepository transactionRepository;

    @InjectMocks
    TransferDetectionService transferDetectionService;

    private static final Long USER_ID = 1L;
    private static final Long STMT_A = 10L;
    private static final Long STMT_B = 20L;

    @Test
    void detect_matchingOppositeTransaction_returnsTrue() {
        LocalDate date = LocalDate.of(2025, 3, 15);
        BigDecimal amount = new BigDecimal("500.00");

        // Existing CREDIT on statement B = counterpart of our DEBIT on statement A
        Transaction counterpart = debit(USER_ID, STMT_B, amount, date.minusDays(1));
        counterpart.setId(99L);

        Transaction tx = credit(USER_ID, STMT_A, amount, date);

        when(transactionRepository.findByUserIdAndDateBetween(
                eq(USER_ID), eq(date.minusDays(7)), eq(date.plusDays(7))))
                .thenReturn(List.of(counterpart));

        boolean result = transferDetectionService.detect(tx, USER_ID, STMT_A);

        assertThat(result).isTrue();
        // counterpart should be marked TRANSFER
        assertThat(counterpart.getClassification()).isEqualTo("TRANSFER");
        verify(transactionRepository).save(counterpart);
    }

    @Test
    void detect_noMatchingTransaction_returnsFalse() {
        LocalDate date = LocalDate.of(2025, 3, 15);
        Transaction tx = debit(USER_ID, STMT_A, new BigDecimal("300.00"), date);

        // No candidates
        when(transactionRepository.findByUserIdAndDateBetween(any(), any(), any()))
                .thenReturn(List.of());

        boolean result = transferDetectionService.detect(tx, USER_ID, STMT_A);

        assertThat(result).isFalse();
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void detect_sameStatement_doesNotMatch() {
        LocalDate date = LocalDate.of(2025, 3, 15);
        BigDecimal amount = new BigDecimal("200.00");

        // Same statement → should be ignored
        Transaction sameStmtTx = credit(USER_ID, STMT_A, amount, date.minusDays(1));
        sameStmtTx.setId(88L);

        Transaction tx = debit(USER_ID, STMT_A, amount, date);

        when(transactionRepository.findByUserIdAndDateBetween(any(), any(), any()))
                .thenReturn(List.of(sameStmtTx));

        boolean result = transferDetectionService.detect(tx, USER_ID, STMT_A);

        assertThat(result).isFalse();
    }

    @Test
    void detect_sameDirection_doesNotMatch() {
        LocalDate date = LocalDate.of(2025, 3, 15);
        BigDecimal amount = new BigDecimal("150.00");

        // Both DEBIT → same direction, not a transfer
        Transaction sameDir = debit(USER_ID, STMT_B, amount, date.minusDays(1));
        sameDir.setId(77L);

        Transaction tx = debit(USER_ID, STMT_A, amount, date);

        when(transactionRepository.findByUserIdAndDateBetween(any(), any(), any()))
                .thenReturn(List.of(sameDir));

        boolean result = transferDetectionService.detect(tx, USER_ID, STMT_A);

        assertThat(result).isFalse();
    }

    @Test
    void detect_differentAmount_doesNotMatch() {
        LocalDate date = LocalDate.of(2025, 3, 15);

        Transaction candidate = credit(USER_ID, STMT_B, new BigDecimal("300.00"), date);
        candidate.setId(66L);

        // tx has different amount
        Transaction tx = debit(USER_ID, STMT_A, new BigDecimal("301.00"), date);

        when(transactionRepository.findByUserIdAndDateBetween(any(), any(), any()))
                .thenReturn(List.of(candidate));

        boolean result = transferDetectionService.detect(tx, USER_ID, STMT_A);

        assertThat(result).isFalse();
    }

    @Test
    void detect_counterpartRetroactivelyMarked() {
        LocalDate date = LocalDate.of(2025, 3, 10);
        BigDecimal amount = new BigDecimal("1000.00");

        Transaction counterpart = debit(USER_ID, STMT_B, amount, date);
        counterpart.setId(55L);
        counterpart.setClassification("EXPENSE");

        Transaction tx = credit(USER_ID, STMT_A, amount, date.plusDays(3));

        when(transactionRepository.findByUserIdAndDateBetween(any(), any(), any()))
                .thenReturn(List.of(counterpart));

        transferDetectionService.detect(tx, USER_ID, STMT_A);

        assertThat(counterpart.getClassification()).isEqualTo("TRANSFER");
        verify(transactionRepository).save(counterpart);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Transaction debit(Long userId, Long stmtId, BigDecimal amount, LocalDate date) {
        return Transaction.builder()
                .userId(userId).statementId(stmtId)
                .date(date).description("Test debit")
                .amount(amount).type(Transaction.TransactionType.DEBIT)
                .classification("EXPENSE").createdAt(LocalDateTime.now()).build();
    }

    private Transaction credit(Long userId, Long stmtId, BigDecimal amount, LocalDate date) {
        return Transaction.builder()
                .userId(userId).statementId(stmtId)
                .date(date).description("Test credit")
                .amount(amount).type(Transaction.TransactionType.CREDIT)
                .classification("INCOME").createdAt(LocalDateTime.now()).build();
    }
}
