package com.financeapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "monthly_summaries")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthlySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Short month;

    @Column(nullable = false)
    private Short year;

    @Column(name = "total_income", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalIncome;

    @Column(name = "total_expenses", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalExpenses;

    @Column(name = "net_savings", nullable = false, precision = 15, scale = 2)
    private BigDecimal netSavings;

    @Column(name = "top_category")
    private String topCategory;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
