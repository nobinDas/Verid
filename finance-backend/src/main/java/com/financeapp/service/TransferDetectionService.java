package com.financeapp.service;

import com.financeapp.model.Transaction;
import com.financeapp.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransferDetectionService {

    private final TransactionRepository transactionRepository;

    /**
     * Returns true if the transaction is an inter-account transfer.
     * Also retroactively marks the matching counterpart as TRANSFER.
     *
     * @param tx          the new transaction being classified
     * @param userId      owner
     * @param statementId the statement this tx belongs to (excluded from search)
     */
    public boolean detect(Transaction tx, Long userId, Long statementId) {
        LocalDate from = tx.getDate().minusDays(7);
        LocalDate to   = tx.getDate().plusDays(7);

        List<Transaction> candidates = transactionRepository
                .findByUserIdAndDateBetween(userId, from, to);

        for (Transaction candidate : candidates) {
            // Must be: different statement, opposite direction, exact same amount
            if (candidate.getStatementId() == null || candidate.getStatementId().equals(statementId)) continue;
            if (candidate.getType() == tx.getType()) continue;
            if (candidate.getAmount().compareTo(tx.getAmount()) != 0) continue;

            // Found a match — retroactively mark the counterpart as TRANSFER
            candidate.setClassification("TRANSFER");
            transactionRepository.save(candidate);
            return true;
        }

        return false;
    }
}
