package com.financeapp.controller;

import com.financeapp.model.User;
import com.financeapp.repository.MonthlySummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/summaries")
@RequiredArgsConstructor
public class SummaryController {

    private final MonthlySummaryRepository summaryRepository;

    record SavingsPoint(int month, int year, BigDecimal netSavings) {}

    @GetMapping
    public ResponseEntity<List<SavingsPoint>> getSavings() {
        Long userId = currentUserId();
        List<SavingsPoint> result = summaryRepository
                .findByUserIdOrderByYearDescMonthDesc(userId)
                .stream()
                .map(s -> new SavingsPoint(s.getMonth(), s.getYear(), s.getNetSavings()))
                .toList();
        return ResponseEntity.ok(result);
    }

    private Long currentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
