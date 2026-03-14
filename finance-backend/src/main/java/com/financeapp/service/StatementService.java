package com.financeapp.service;

import com.financeapp.dto.statement.StatementResponse;
import com.financeapp.dto.statement.TransactionResponse;
import com.financeapp.model.BankStatement;
import com.financeapp.model.Transaction;
import com.financeapp.repository.BankStatementRepository;
import com.financeapp.repository.TransactionRepository;
import com.financeapp.service.parser.ParsedStatement;
import com.financeapp.service.parser.ParsedTransaction;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatementService {

    private final BankStatementRepository bankStatementRepository;
    private final TransactionRepository transactionRepository;
    private final StatementParserService statementParserService;

    public StatementResponse upload(Long userId, MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }

        // Compute SHA-256 hash
        String hash;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(bytes);
            hash = HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }

        // Duplicate file check
        bankStatementRepository.findByUserIdAndFileHash(userId, hash).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This statement has already been uploaded");
        });

        // Parse statement for metadata — surface parse errors as 400 so the frontend shows the real reason
        ParsedStatement parsed;
        try {
            parsed = statementParserService.parse(bytes);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Throwable t) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Failed to parse PDF: " + t.getClass().getSimpleName() + " — " + t.getMessage());
        }

        short month = (short) parsed.periodStart().getMonthValue();
        short year = (short) parsed.periodStart().getYear();

        // Duplicate period check
        if (bankStatementRepository.existsByUserIdAndBankNameAndAccountLast4AndMonthAndYear(
                userId, parsed.bankName(), parsed.accountLast4(), month, year)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A statement for this account and period already exists");
        }

        BankStatement statement = BankStatement.builder()
                .userId(userId)
                .filename(file.getOriginalFilename())
                .uploadDate(LocalDateTime.now())
                .processed(false)
                .fileData(bytes)
                .fileHash(hash)
                .bankName(parsed.bankName())
                .accountLast4(parsed.accountLast4())
                .accountType(parsed.accountType())
                .month(month)
                .year(year)
                .build();

        BankStatement saved = bankStatementRepository.save(statement);
        return toResponse(saved, 0L);
    }

    public List<StatementResponse> findAll(Long userId) {
        return bankStatementRepository.findByUserIdOrderByYearDescMonthDesc(userId).stream()
                .map(s -> toResponse(s, transactionRepository.countByStatementId(s.getId())))
                .toList();
    }

    public StatementResponse process(Long userId, Long statementId) {
        BankStatement statement = bankStatementRepository.findByIdAndUserId(statementId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Statement not found"));

        if (statement.isProcessed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Already processed");
        }

        ParsedStatement parsed;
        try {
            parsed = statementParserService.parse(statement.getFileData());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        for (ParsedTransaction pt : parsed.transactions()) {
            Transaction tx = Transaction.builder()
                    .statementId(statementId)
                    .userId(userId)
                    .date(pt.date())
                    .description(pt.description())
                    .amount(pt.amount())
                    .type(pt.type())
                    .createdAt(LocalDateTime.now())
                    .build();
            transactionRepository.save(tx);
        }

        statement.setProcessed(true);
        statement.setFileData(null);
        bankStatementRepository.save(statement);

        long count = transactionRepository.countByStatementId(statementId);
        return toResponse(statement, count);
    }

    public void delete(Long userId, Long statementId) {
        BankStatement statement = bankStatementRepository.findByIdAndUserId(statementId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Statement not found"));
        bankStatementRepository.delete(statement);
    }

    public List<TransactionResponse> getTransactions(Long userId, Long statementId) {
        bankStatementRepository.findByIdAndUserId(statementId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Statement not found"));
        return transactionRepository.findByStatementIdOrderByDateDesc(statementId).stream()
                .map(this::toTxResponse)
                .toList();
    }

    private StatementResponse toResponse(BankStatement s, long count) {
        return new StatementResponse(
                s.getId(),
                s.getFilename(),
                s.getBankName(),
                s.getAccountLast4(),
                s.getAccountType(),
                s.getMonth() != null ? s.getMonth() : 0,
                s.getYear() != null ? s.getYear() : 0,
                s.isProcessed(),
                count,
                s.getUploadDate()
        );
    }

    private TransactionResponse toTxResponse(Transaction t) {
        return new TransactionResponse(
                t.getId(),
                t.getDate(),
                t.getDescription(),
                t.getAmount(),
                t.getType() != null ? t.getType().name() : null,
                t.getCategory(),
                t.getCreatedAt()
        );
    }
}
