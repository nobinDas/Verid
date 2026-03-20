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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SummaryControllerTest extends BaseControllerTest {

    @MockitoBean TransactionRepository transactionRepository;
    @MockitoBean CashEntryRepository cashEntryRepository;

    @Test
    void getSavings_noData_returnsEmptyList() throws Exception {
        when(transactionRepository.findByUserIdOrderByDateDesc(USER_ID_A)).thenReturn(List.of());
        when(cashEntryRepository.findByUserIdOrderByDateDesc(USER_ID_A)).thenReturn(List.of());

        mockMvc.perform(asUserA(get("/api/summaries")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getSavings_withIncomeAndExpenses_returnsMonthlySummary() throws Exception {
        Transaction income = Transaction.builder()
                .userId(USER_ID_A).statementId(1L)
                .date(LocalDate.of(2025, 3, 1)).description("Salary")
                .amount(new BigDecimal("4000.00")).type(Transaction.TransactionType.CREDIT)
                .classification("INCOME").createdAt(LocalDateTime.now()).build();

        Transaction expense = Transaction.builder()
                .userId(USER_ID_A).statementId(1L)
                .date(LocalDate.of(2025, 3, 15)).description("Rent")
                .amount(new BigDecimal("1200.00")).type(Transaction.TransactionType.DEBIT)
                .classification("EXPENSE").createdAt(LocalDateTime.now()).build();

        when(transactionRepository.findByUserIdOrderByDateDesc(USER_ID_A))
                .thenReturn(List.of(income, expense));
        when(cashEntryRepository.findByUserIdOrderByDateDesc(USER_ID_A)).thenReturn(List.of());

        mockMvc.perform(asUserA(get("/api/summaries")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].totalIncome").value(4000.00))
                .andExpect(jsonPath("$[0].totalExpenses").value(1200.00))
                .andExpect(jsonPath("$[0].netSavings").value(2800.00));
    }

    @Test
    void getSavings_multipleMonths_orderedDescending() throws Exception {
        Transaction feb = Transaction.builder()
                .userId(USER_ID_A).statementId(1L)
                .date(LocalDate.of(2025, 2, 1)).description("Feb Salary")
                .amount(new BigDecimal("3000.00")).type(Transaction.TransactionType.CREDIT)
                .classification("INCOME").createdAt(LocalDateTime.now()).build();

        Transaction mar = Transaction.builder()
                .userId(USER_ID_A).statementId(1L)
                .date(LocalDate.of(2025, 3, 1)).description("Mar Salary")
                .amount(new BigDecimal("3500.00")).type(Transaction.TransactionType.CREDIT)
                .classification("INCOME").createdAt(LocalDateTime.now()).build();

        when(transactionRepository.findByUserIdOrderByDateDesc(USER_ID_A)).thenReturn(List.of(feb, mar));
        when(cashEntryRepository.findByUserIdOrderByDateDesc(USER_ID_A)).thenReturn(List.of());

        mockMvc.perform(asUserA(get("/api/summaries")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // Most recent month (March) first
                .andExpect(jsonPath("$[0].month").value(3))
                .andExpect(jsonPath("$[1].month").value(2));
    }

    @Test
    void getSavings_onlyOwnData_repositoryCalledWithCorrectUserId() throws Exception {
        when(transactionRepository.findByUserIdOrderByDateDesc(USER_ID_A)).thenReturn(List.of());
        when(cashEntryRepository.findByUserIdOrderByDateDesc(USER_ID_A)).thenReturn(List.of());

        mockMvc.perform(asUserA(get("/api/summaries")))
                .andExpect(status().isOk());

        verify(transactionRepository).findByUserIdOrderByDateDesc(USER_ID_A);
        verify(cashEntryRepository).findByUserIdOrderByDateDesc(USER_ID_A);
    }

    @Test
    void getSavings_withCashInEntry_includesInIncome() throws Exception {
        CashEntry cashIn = CashEntry.builder()
                .userId(USER_ID_A).date(LocalDate.of(2025, 3, 10))
                .description("Cash gift").amount(new BigDecimal("500.00"))
                .type("IN").createdAt(LocalDateTime.now()).build();

        when(transactionRepository.findByUserIdOrderByDateDesc(USER_ID_A)).thenReturn(List.of());
        when(cashEntryRepository.findByUserIdOrderByDateDesc(USER_ID_A)).thenReturn(List.of(cashIn));

        mockMvc.perform(asUserA(get("/api/summaries")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].totalIncome").value(500.00));
    }

    @Test
    void getSavings_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/summaries"))
                .andExpect(status().isUnauthorized());
    }
}
