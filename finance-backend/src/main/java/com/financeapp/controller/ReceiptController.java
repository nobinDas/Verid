package com.financeapp.controller;

import com.financeapp.dto.receipt.ReceiptResponse;
import com.financeapp.model.User;
import com.financeapp.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    @PostMapping("/upload")
    public ResponseEntity<ReceiptResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "retentionYears", defaultValue = "1") int retentionYears) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(receiptService.upload(currentUserId(), file, retentionYears));
    }

    @GetMapping
    public ResponseEntity<List<ReceiptResponse>> getAll() {
        return ResponseEntity.ok(receiptService.findAll(currentUserId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        receiptService.delete(currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
