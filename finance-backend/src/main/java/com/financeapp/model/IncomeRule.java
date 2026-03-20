package com.financeapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "income_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String pattern;

    @Column(name = "is_income", nullable = false)
    private boolean income;

    @Column(name = "seen_count", nullable = false)
    @Builder.Default
    private int seenCount = 1;

    @Column(name = "last_seen")
    private LocalDate lastSeen;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
