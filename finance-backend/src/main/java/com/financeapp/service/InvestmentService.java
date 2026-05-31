package com.financeapp.service;

import com.financeapp.dto.investment.InvestmentRequest;
import com.financeapp.dto.investment.InvestmentResponse;
import com.financeapp.model.Investment;
import com.financeapp.model.Transaction;
import com.financeapp.repository.InvestmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvestmentService {

    private final InvestmentRepository investmentRepository;

    public List<InvestmentResponse> findAll(Long userId) {
        return investmentRepository.findByUserIdOrderByInvestedAtDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    public InvestmentResponse create(Long userId, InvestmentRequest req) {
        Investment investment = Investment.builder()
                .userId(userId)
                .source(Investment.Source.MANUAL)
                .platform(req.platform())
                .amount(req.amount())
                .investedAt(req.investedAt())
                .recipient(req.recipient())
                .contactInfo(req.contactInfo())
                .expectedReturnDate(req.expectedReturnDate())
                .interestRate(req.interestRate())
                .notes(req.notes())
                .createdAt(LocalDateTime.now())
                .build();
        return toResponse(investmentRepository.save(investment));
    }

    public InvestmentResponse update(Long userId, Long id, InvestmentRequest req) {
        Investment investment = investmentRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Investment not found"));

        if (investment.getSource() == Investment.Source.STATEMENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Auto-detected investments cannot be edited");
        }

        investment.setPlatform(req.platform());
        investment.setAmount(req.amount());
        investment.setInvestedAt(req.investedAt());
        investment.setRecipient(req.recipient());
        investment.setContactInfo(req.contactInfo());
        investment.setExpectedReturnDate(req.expectedReturnDate());
        investment.setInterestRate(req.interestRate());
        investment.setNotes(req.notes());
        return toResponse(investmentRepository.save(investment));
    }

    public void delete(Long userId, Long id) {
        Investment investment = investmentRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Investment not found"));

        if (investment.getSource() == Investment.Source.STATEMENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Auto-detected investments cannot be deleted");
        }

        investmentRepository.delete(investment);
    }

    public void createFromTransaction(Long userId, Transaction tx, String platform) {
        if (investmentRepository.existsByTransactionId(tx.getId())) return;

        Investment investment = Investment.builder()
                .userId(userId)
                .source(Investment.Source.STATEMENT)
                .platform(platform)
                .amount(tx.getAmount())
                .investedAt(tx.getDate())
                .transactionId(tx.getId())
                .notes(tx.getDescription())
                .createdAt(LocalDateTime.now())
                .build();
        investmentRepository.save(investment);
    }

    private InvestmentResponse toResponse(Investment i) {
        Long daysUntilReturn = null;
        BigDecimal expectedReturn = null;

        if (i.getExpectedReturnDate() != null) {
            daysUntilReturn = ChronoUnit.DAYS.between(LocalDate.now(), i.getExpectedReturnDate());
        }

        if (i.getInterestRate() != null) {
            BigDecimal rate = i.getInterestRate().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            expectedReturn = i.getAmount().multiply(BigDecimal.ONE.add(rate)).setScale(2, RoundingMode.HALF_UP);
        }

        return new InvestmentResponse(
                i.getId(),
                i.getSource().name(),
                i.getPlatform(),
                i.getAmount(),
                i.getInvestedAt(),
                i.getTransactionId(),
                i.getRecipient(),
                i.getContactInfo(),
                i.getExpectedReturnDate(),
                i.getInterestRate(),
                i.getNotes(),
                i.getCreatedAt(),
                daysUntilReturn,
                expectedReturn
        );
    }
}
