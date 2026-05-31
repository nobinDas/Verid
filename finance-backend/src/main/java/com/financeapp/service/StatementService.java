package com.financeapp.service;

import com.financeapp.dto.statement.PendingReviewItem;
import com.financeapp.dto.statement.PendingSheetItem;
import com.financeapp.dto.statement.ReviewAnswer;
import com.financeapp.dto.statement.SheetMappingAnswer;
import com.financeapp.dto.statement.StatementResponse;
import com.financeapp.dto.statement.TransactionResponse;
import com.financeapp.model.BankStatement;
import com.financeapp.model.MonthlySummary;
import com.financeapp.model.StatementUploadHistory;
import com.financeapp.model.Transaction;
import com.financeapp.repository.BankStatementRepository;
import com.financeapp.repository.InvestmentRepository;
import com.financeapp.repository.MonthlySummaryRepository;
import com.financeapp.repository.StatementUploadHistoryRepository;
import com.financeapp.repository.TransactionRepository;
import com.financeapp.service.parser.ParsedStatement;
import com.financeapp.service.parser.ParsedTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatementService {

    private final BankStatementRepository bankStatementRepository;
    private final TransactionRepository transactionRepository;
    private final MonthlySummaryRepository monthlySummaryRepository;
    private final StatementParserService statementParserService;
    private final AICategoryService aiCategoryService;
    private final TransferDetectionService transferDetectionService;
    private final IncomeClassificationService incomeClassificationService;
    private final StatementUploadHistoryRepository uploadHistoryRepository;
    private final InvestmentService investmentService;
    private final InvestmentRepository investmentRepository;

    @Autowired(required = false)
    private GoogleSheetsService googleSheetsService;

    private static final Map<String, String> INVESTMENT_PLATFORMS = Map.of(
            "fidelity", "Fidelity",
            "fid bkg svc", "Fidelity",
            "coinbase", "Coinbase"
    );

    private static final DateTimeFormatter HISTORY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a");

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

        // Check permanent upload history — catches re-uploads of previously deleted statements
        uploadHistoryRepository.findByUserIdAndFileHash(userId, hash).ifPresent(history -> {
            String when = history.getUploadedAt().format(HISTORY_FMT);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This statement was already uploaded on " + when);
        });

        // Check if currently active (not yet deleted)
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

        // Record in permanent upload history
        uploadHistoryRepository.save(StatementUploadHistory.builder()
                .userId(userId)
                .fileHash(hash)
                .filename(file.getOriginalFilename())
                .build());

        return toResponse(saved, 0L);
    }

    public List<StatementResponse> findAll(Long userId) {
        return bankStatementRepository.findByUserIdOrderByYearDescMonthDesc(userId).stream()
                .map(s -> toResponse(s, transactionRepository.countByStatementId(s.getId())))
                .toList();
    }

    @Transactional
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

        boolean isCreditCard = "CREDIT".equalsIgnoreCase(statement.getAccountType());

        // pattern → [savedId, description, totalAmount]
        // Using LinkedHashMap to preserve encounter order
        Map<String, List<Long>>       pendingIds    = new LinkedHashMap<>();
        Map<String, String>           pendingDesc   = new LinkedHashMap<>();
        Map<String, BigDecimal>       pendingTotal  = new LinkedHashMap<>();

        // Pre-resolve all categories in a single batched AI call (with cache)
        List<String> allDescriptions = parsed.transactions().stream()
                .map(ParsedTransaction::description)
                .toList();
        Map<String, String> categoryMap = aiCategoryService.categorizeAll(allDescriptions);

        for (ParsedTransaction pt : parsed.transactions()) {
            String category = categoryMap.getOrDefault(pt.description(), "Other");
            String classification = classifyTransaction(pt, isCreditCard, userId, statementId);

            Transaction tx = Transaction.builder()
                    .statementId(statementId)
                    .userId(userId)
                    .date(pt.date())
                    .description(pt.description())
                    .amount(pt.amount())
                    .type(pt.type())
                    .category(category)
                    .classification(classification)
                    .createdAt(LocalDateTime.now())
                    .build();
            Transaction saved = transactionRepository.save(tx);
            detectAndSaveInvestment(userId, saved);

            if ("UNCLASSIFIED".equals(classification)) {
                String pattern = IncomeClassificationService.normalize(pt.description());
                pendingIds.computeIfAbsent(pattern, k -> new ArrayList<>()).add(saved.getId());
                pendingDesc.putIfAbsent(pattern, pt.description());
                pendingTotal.merge(pattern, pt.amount(), BigDecimal::add);
            }
        }

        List<PendingReviewItem> pendingReviews = pendingIds.entrySet().stream()
                .map(e -> {
                    String pattern = e.getKey();
                    List<Long> ids = e.getValue();
                    return new PendingReviewItem(
                            ids,
                            pendingDesc.get(pattern),
                            ids.size(),
                            pendingTotal.get(pattern));
                })
                .toList();

        // Recalculate monthly summary based on classified transactions
        rebuildMonthlySummary(userId, statement.getMonth(), statement.getYear());

        statement.setProcessed(true);
        statement.setFileData(null);
        bankStatementRepository.save(statement);

        // Write to Google Sheets — grouped by month (statements can span multiple months)
        List<PendingSheetItem> pendingSheetMappings = new ArrayList<>();
        if (googleSheetsService != null) {
            try {
                List<Transaction> savedTxs = transactionRepository.findByStatementIdOrderByDateDesc(statementId);
                Map<Integer, List<Transaction>> byMonth = savedTxs.stream()
                        .collect(Collectors.groupingBy(t -> t.getDate().getYear() * 100 + t.getDate().getMonthValue()));
                for (Map.Entry<Integer, List<Transaction>> entry : byMonth.entrySet()) {
                    int yr = entry.getKey() / 100;
                    int mo = entry.getKey() % 100;
                    GoogleSheetsService.WriteResult result = googleSheetsService.writeTransactionsForMonth(entry.getValue(), mo, yr);
                    log.info("Google Sheets: {}", result.summary());
                    pendingSheetMappings.addAll(result.unmapped());
                }
            } catch (Exception e) {
                log.warn("Google Sheets sync failed (statement still processed): {}", e.getMessage());
            }
        }

        long count = transactionRepository.countByStatementId(statementId);
        return toResponseWithReviews(statement, count, pendingReviews, pendingSheetMappings);
    }

    @Transactional
    public void review(Long userId, Long statementId, List<ReviewAnswer> answers) {
        // Verify statement belongs to user
        bankStatementRepository.findByIdAndUserId(statementId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Statement not found"));

        for (ReviewAnswer answer : answers) {
            String classification = answer.isIncome() ? "INCOME" : "EXTRA_IN";
            String ruleDescription = null;

            for (Long txId : answer.transactionIds()) {
                Transaction tx = transactionRepository.findById(txId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Transaction not found: " + txId));

                if (!tx.getUserId().equals(userId)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
                }

                tx.setClassification(classification);
                transactionRepository.save(tx);

                // Save rule once per group (pattern is the same for all IDs in the group)
                if (ruleDescription == null) {
                    ruleDescription = tx.getDescription();
                    incomeClassificationService.saveRule(userId, ruleDescription, answer.isIncome(), tx.getDate());
                }
            }
        }

        // Rebuild monthly summaries for affected months
        BankStatement statement = bankStatementRepository.findByIdAndUserId(statementId, userId).get();
        rebuildMonthlySummary(userId, statement.getMonth(), statement.getYear());
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

    // ── Investment detection ──────────────────────────────────────────────────

    private void detectAndSaveInvestment(Long userId, Transaction tx) {
        if (tx.getType() != Transaction.TransactionType.DEBIT) return;
        String desc = tx.getDescription().toLowerCase();
        INVESTMENT_PLATFORMS.forEach((keyword, platform) -> {
            if (desc.contains(keyword) && !investmentRepository.existsByTransactionId(tx.getId())) {
                investmentService.createFromTransaction(userId, tx, platform);
            }
        });
    }

    // ── Classification logic ──────────────────────────────────────────────────

    private String classifyTransaction(ParsedTransaction pt, boolean isCreditCard,
                                       Long userId, Long statementId) {
        if (isCreditCard) {
            return pt.type() == Transaction.TransactionType.DEBIT ? "CC_CHARGE" : "CC_PAYMENT";
        }

        // Checking or Savings
        if (pt.type() == Transaction.TransactionType.DEBIT) {
            // Need a temporary tx object for transfer detection (not yet saved)
            Transaction tempTx = Transaction.builder()
                    .userId(userId)
                    .statementId(statementId)
                    .date(pt.date())
                    .amount(pt.amount())
                    .type(pt.type())
                    .build();
            return transferDetectionService.detect(tempTx, userId, statementId)
                    ? "TRANSFER" : "EXPENSE";
        } else {
            // CREDIT on checking/savings — check transfer first
            Transaction tempTx = Transaction.builder()
                    .userId(userId)
                    .statementId(statementId)
                    .date(pt.date())
                    .amount(pt.amount())
                    .type(pt.type())
                    .build();
            if (transferDetectionService.detect(tempTx, userId, statementId)) {
                return "TRANSFER";
            }
            return incomeClassificationService.classify(userId, pt.description(), pt.date());
        }
    }

    // ── Monthly summary ───────────────────────────────────────────────────────

    private void rebuildMonthlySummary(Long userId, Short month, Short year) {
        List<Transaction> allUserTx = transactionRepository.findByUserIdOrderByDateDesc(userId);

        BigDecimal totalIncome   = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;

        for (Transaction t : allUserTx) {
            if (t.getDate().getMonthValue() != month || t.getDate().getYear() != year) continue;
            String cls = t.getClassification();
            if ("INCOME".equals(cls) || "CASH_IN".equals(cls)) {
                totalIncome = totalIncome.add(t.getAmount());
            } else if ("EXPENSE".equals(cls) || "CASH_OUT".equals(cls)) {
                totalExpenses = totalExpenses.add(t.getAmount());
            }
        }

        MonthlySummary summary = monthlySummaryRepository
                .findByUserIdAndMonthAndYear(userId, month, year)
                .orElseGet(() -> {
                    MonthlySummary s = new MonthlySummary();
                    s.setUserId(userId);
                    s.setMonth(month);
                    s.setYear(year);
                    s.setCreatedAt(LocalDateTime.now());
                    return s;
                });
        summary.setTotalIncome(totalIncome);
        summary.setTotalExpenses(totalExpenses);
        summary.setNetSavings(totalIncome.subtract(totalExpenses));
        monthlySummaryRepository.save(summary);
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

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

    private StatementResponse toResponseWithReviews(BankStatement s, long count,
                                                     List<PendingReviewItem> pendingReviews,
                                                     List<PendingSheetItem> pendingSheetMappings) {
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
                s.getUploadDate(),
                pendingReviews,
                pendingSheetMappings
        );
    }

    public void sheetReview(Long userId, Long statementId, List<SheetMappingAnswer> answers) {
        bankStatementRepository.findByIdAndUserId(statementId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Statement not found"));

        if (googleSheetsService == null || answers == null || answers.isEmpty()) return;

        List<Long> allIds = answers.stream()
                .flatMap(a -> a.transactionIds().stream())
                .distinct().toList();

        Map<Long, Transaction> txById = new HashMap<>();
        transactionRepository.findAllById(allIds).forEach(tx -> txById.put(tx.getId(), tx));

        // Group answers by month based on representative transaction date
        Map<Integer, List<SheetMappingAnswer>> byMonth = new LinkedHashMap<>();
        for (SheetMappingAnswer answer : answers) {
            Transaction repTx = answer.transactionIds().stream()
                    .map(txById::get).filter(Objects::nonNull).findFirst().orElse(null);
            if (repTx == null) continue;
            int key = repTx.getDate().getYear() * 100 + repTx.getDate().getMonthValue();
            byMonth.computeIfAbsent(key, k -> new ArrayList<>()).add(answer);
        }

        for (Map.Entry<Integer, List<SheetMappingAnswer>> entry : byMonth.entrySet()) {
            int yr = entry.getKey() / 100;
            int mo = entry.getKey() % 100;
            try {
                googleSheetsService.writeReviewedTransactions(mo, yr, entry.getValue(), txById);
                log.info("Sheet review written for {}/{}", mo, yr);
            } catch (Exception e) {
                log.warn("Sheet review write failed for {}/{}: {}", mo, yr, e.getMessage());
            }
        }
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
