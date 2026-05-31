package com.financeapp.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "investments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Investment {

    public enum Source { STATEMENT, MANUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source;

    @Column(nullable = false)
    private String platform;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "invested_at", nullable = false)
    private LocalDate investedAt;

    @Column(name = "transaction_id")
    private Long transactionId;

    private String recipient;

    @Column(name = "contact_info")
    private String contactInfo;

    @Column(name = "expected_return_date")
    private LocalDate expectedReturnDate;

    @Column(name = "interest_rate", precision = 5, scale = 2)
    private BigDecimal interestRate;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
