package com.financeapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bank_statements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String filename;

    @Column(name = "upload_date", nullable = false)
    @Builder.Default
    private LocalDateTime uploadDate = LocalDateTime.now();

    @Column(nullable = false)
    private Short month;

    @Column(nullable = false)
    private Short year;

    @Column(nullable = false)
    @Builder.Default
    private boolean processed = false;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "account_last4")
    private String accountLast4;

    @Column(name = "account_type")
    private String accountType;

    @Column(name = "file_hash")
    private String fileHash;

    @Column(name = "file_data")
    private byte[] fileData;
}
