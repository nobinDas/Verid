package com.financeapp.controller;

import com.financeapp.dto.investment.InvestmentRequest;
import com.financeapp.dto.investment.InvestmentResponse;
import com.financeapp.model.User;
import com.financeapp.service.InvestmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/investments")
@RequiredArgsConstructor
public class InvestmentController {

    private final InvestmentService investmentService;

    private Long userId() {
        return ((User) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId();
    }

    @GetMapping
    public List<InvestmentResponse> findAll() {
        return investmentService.findAll(userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvestmentResponse create(@Valid @RequestBody InvestmentRequest req) {
        return investmentService.create(userId(), req);
    }

    @PutMapping("/{id}")
    public InvestmentResponse update(@PathVariable Long id, @Valid @RequestBody InvestmentRequest req) {
        return investmentService.update(userId(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        investmentService.delete(userId(), id);
    }
}
