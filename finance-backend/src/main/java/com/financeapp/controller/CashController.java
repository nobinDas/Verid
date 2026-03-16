package com.financeapp.controller;

import com.financeapp.dto.cash.CashEntryRequest;
import com.financeapp.dto.cash.CashEntryResponse;
import com.financeapp.model.CashEntry;
import com.financeapp.model.User;
import com.financeapp.repository.CashEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/cash")
@RequiredArgsConstructor
public class CashController {

    private final CashEntryRepository cashEntryRepository;

    @PostMapping
    public ResponseEntity<CashEntryResponse> create(@RequestBody CashEntryRequest request) {
        Long userId = currentUserId();
        CashEntry entry = CashEntry.builder()
                .userId(userId)
                .date(request.date())
                .description(request.description())
                .amount(request.amount())
                .type(request.type())
                .category(request.category())
                .createdAt(LocalDateTime.now())
                .build();
        CashEntry saved = cashEntryRepository.save(entry);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @GetMapping
    public ResponseEntity<List<CashEntryResponse>> getAll() {
        Long userId = currentUserId();
        List<CashEntryResponse> result = cashEntryRepository.findByUserIdOrderByDateDesc(userId)
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Long userId = currentUserId();
        CashEntry entry = cashEntryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cash entry not found"));
        if (!entry.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        cashEntryRepository.delete(entry);
        return ResponseEntity.noContent().build();
    }

    private CashEntryResponse toResponse(CashEntry e) {
        return new CashEntryResponse(
                e.getId(), e.getDate(), e.getDescription(),
                e.getAmount(), e.getType(), e.getCategory(), e.getCreatedAt());
    }

    private Long currentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
