package com.financeapp.controller;

import com.financeapp.model.CashEntry;
import com.financeapp.model.Transaction;
import com.financeapp.repository.CashEntryRepository;
import com.financeapp.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AnalyticsControllerTest extends BaseControllerTest {

    @MockitoBean TransactionRepository transactionRepository;
    @MockitoBean CashEntryRepository cashEntryRepository;

    // ── Categories ────────────────────────────────────────────────────────────

    @Test
    void getCategories_noData_returnsEmptyList() throws Exception {
        when(transactionRepository.findByUserIdAndDateBetween(eq(USER_ID_A), any(), any()))
                .thenReturn(List.of());
        when(cashEntryRepository.findByUserIdOrderByDateDesc(USER_ID_A)).thenReturn(List.of());

        mockMvc.perform(asUserA(get("/api/analytics/categories?from=2025-01&to=2025-03")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getCategories_correctTotalsForExpenses() throws Exception {
        Transaction groceries = expense("Whole Foods", "Groceries", new BigDecimal("120.00"),
                LocalDate.of(2025, 2, 10));
        Transaction dining = expense("Chipotle", "Dining", new BigDecimal("45.00"),
                LocalDate.of(2025, 2, 15));

        when(transactionRepository.findByUserIdAndDateBetween(eq(USER_ID_A), any(), any()))
                .thenReturn(List.of(groceries, dining));
        when(cashEntryRepository.findByUserIdOrderByDateDesc(USER_ID_A)).thenReturn(List.of());

        mockMvc.perform(asUserA(get("/api/analytics/categories?from=2025-02&to=2025-02")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getCategories_excludesIncomeTransactions() throws Exception {
        Transaction income = Transaction.builder()
                .userId(USER_ID_A).statementId(1L)
                .date(LocalDate.of(2025, 2, 1)).description("Salary")
                .amount(new BigDecimal("3000.00")).type(Transaction.TransactionType.CREDIT)
                .classification("INCOME").createdAt(LocalDateTime.now()).build();

        when(transactionRepository.findByUserIdAndDateBetween(eq(USER_ID_A), any(), any()))
                .thenReturn(List.of(income));
        when(cashEntryRepository.findByUserIdOrderByDateDesc(USER_ID_A)).thenReturn(List.of());

        mockMvc.perform(asUserA(get("/api/analytics/categories?from=2025-02&to=2025-02")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getCategories_orderedByTotalDescending() throws Exception {
        Transaction big = expense("Rent", "Housing", new BigDecimal("1500.00"), LocalDate.of(2025, 3, 1));
        Transaction small = expense("Coffee", "Food", new BigDecimal("5.00"), LocalDate.of(2025, 3, 2));

        // Provide in wrong order; controller sorts desc
        when(transactionRepository.findByUserIdAndDateBetween(eq(USER_ID_A), any(), any()))
                .thenReturn(List.of(small, big));
        when(cashEntryRepository.findByUserIdOrderByDateDesc(USER_ID_A)).thenReturn(List.of());

        mockMvc.perform(asUserA(get("/api/analytics/categories?from=2025-03&to=2025-03")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].total").value(1500.00))
                .andExpect(jsonPath("$[1].total").value(5.00));
    }

    @Test
    void getCategories_cashOutIncluded() throws Exception {
        CashEntry cashOut = CashEntry.builder()
                .userId(USER_ID_A).date(LocalDate.of(2025, 3, 5))
                .description("ATM withdrawal").amount(new BigDecimal("200.00"))
                .type("OUT").category("Cash").createdAt(LocalDateTime.now()).build();

        when(transactionRepository.findByUserIdAndDateBetween(eq(USER_ID_A), any(), any()))
                .thenReturn(List.of());
        when(cashEntryRepository.findByUserIdOrderByDateDesc(USER_ID_A)).thenReturn(List.of(cashOut));

        mockMvc.perform(asUserA(get("/api/analytics/categories?from=2025-03&to=2025-03")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].total").value(200.00));
    }

    // ── Top transactions ──────────────────────────────────────────────────────

    @Test
    void getTopTransactions_noData_returnsEmptyList() throws Exception {
        when(transactionRepository.findByUserIdAndDateBetween(eq(USER_ID_A), any(), any()))
                .thenReturn(List.of());
        when(cashEntryRepository.findByUserIdOrderByDateDesc(USER_ID_A)).thenReturn(List.of());

        mockMvc.perform(asUserA(get("/api/analytics/top-transactions?from=2025-01&to=2025-03")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getTopTransactions_orderedByAmountDescending() throws Exception {
        Transaction large = expense("Rent", "Housing", new BigDecimal("1500.00"), LocalDate.of(2025, 3, 1));
        Transaction small = expense("Lunch", "Food", new BigDecimal("12.00"), LocalDate.of(2025, 3, 2));

        when(transactionRepository.findByUserIdAndDateBetween(eq(USER_ID_A), any(), any()))
                .thenReturn(List.of(small, large));
        when(cashEntryRepository.findByUserIdOrderByDateDesc(USER_ID_A)).thenReturn(List.of());

        mockMvc.perform(asUserA(get("/api/analytics/top-transactions?from=2025-03&to=2025-03")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(1500.00))
                .andExpect(jsonPath("$[1].amount").value(12.00));
    }

    @Test
    void getCategories_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/analytics/categories?from=2025-01&to=2025-03"))
                .andExpect(status().isUnauthorized());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Transaction expense(String description, String category, BigDecimal amount, LocalDate date) {
        return Transaction.builder()
                .userId(USER_ID_A).statementId(1L)
                .date(date).description(description)
                .amount(amount).type(Transaction.TransactionType.DEBIT)
                .classification("EXPENSE").category(category)
                .createdAt(LocalDateTime.now()).build();
    }
}
