package com.financeapp.controller;

import com.financeapp.dto.statement.ReviewAnswer;
import com.financeapp.dto.statement.StatementResponse;
import com.financeapp.dto.statement.TransactionResponse;
import com.financeapp.model.User;
import com.financeapp.service.StatementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/statements")
@RequiredArgsConstructor
public class StatementController {

    private final StatementService statementService;

    @PostMapping("/upload")
    public ResponseEntity<StatementResponse> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(statementService.upload(currentUserId(), file));
    }

    @GetMapping
    public ResponseEntity<List<StatementResponse>> getAll() {
        return ResponseEntity.ok(statementService.findAll(currentUserId()));
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<StatementResponse> process(@PathVariable Long id) {
        return ResponseEntity.ok(statementService.process(currentUserId(), id));
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<Void> review(@PathVariable Long id, @RequestBody List<ReviewAnswer> answers) {
        statementService.review(currentUserId(), id, answers);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        statementService.delete(currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<TransactionResponse>> getTransactions(@PathVariable Long id) {
        return ResponseEntity.ok(statementService.getTransactions(currentUserId(), id));
    }

    private Long currentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
