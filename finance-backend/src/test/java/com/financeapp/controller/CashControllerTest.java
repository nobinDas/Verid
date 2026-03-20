package com.financeapp.controller;

import com.financeapp.model.CashEntry;
import com.financeapp.repository.CashEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CashControllerTest extends BaseControllerTest {

    @MockitoBean CashEntryRepository cashEntryRepository;

    // ── Create ────────────────────────────────────────────────────────────────

    @Test
    void createCashIn_success_returns201() throws Exception {
        CashEntry saved = cashEntry(USER_ID_A, "IN", new BigDecimal("250.00"));
        saved.setId(1L);
        when(cashEntryRepository.save(any())).thenReturn(saved);

        mockMvc.perform(asUserA(post("/api/cash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2025-03-01\",\"description\":\"Freelance payment\",\"amount\":250.00,\"type\":\"IN\",\"category\":\"Freelance\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("IN"))
                .andExpect(jsonPath("$.amount").value(250.00));
    }

    @Test
    void createCashOut_success_returns201() throws Exception {
        CashEntry saved = cashEntry(USER_ID_A, "OUT", new BigDecimal("50.00"));
        saved.setId(2L);
        when(cashEntryRepository.save(any())).thenReturn(saved);

        mockMvc.perform(asUserA(post("/api/cash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2025-03-01\",\"description\":\"Coffee shop\",\"amount\":50.00,\"type\":\"OUT\",\"category\":\"Food\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("OUT"));
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @Test
    void getAll_success_returnsOwnEntries() throws Exception {
        List<CashEntry> entries = List.of(
                cashEntry(USER_ID_A, "IN", new BigDecimal("100.00")),
                cashEntry(USER_ID_A, "OUT", new BigDecimal("50.00")));
        when(cashEntryRepository.findByUserIdOrderByDateDesc(USER_ID_A)).thenReturn(entries);

        mockMvc.perform(asUserA(get("/api/cash")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getAll_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/cash"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAll_ownDataOnly_userBSeesOwnEntries() throws Exception {
        // User B has 1 entry
        when(cashEntryRepository.findByUserIdOrderByDateDesc(USER_ID_B))
                .thenReturn(List.of(cashEntry(USER_ID_B, "OUT", new BigDecimal("20.00"))));

        mockMvc.perform(asUserB(get("/api/cash")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_success_returns204() throws Exception {
        CashEntry entry = cashEntry(USER_ID_A, "OUT", new BigDecimal("50.00"));
        entry.setId(1L);
        when(cashEntryRepository.findById(1L)).thenReturn(Optional.of(entry));

        mockMvc.perform(asUserA(delete("/api/cash/1")))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        when(cashEntryRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(asUserA(delete("/api/cash/99")))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_notOwner_returns403() throws Exception {
        // Entry belongs to USER_B — User A tries to delete it
        CashEntry entry = cashEntry(USER_ID_B, "OUT", new BigDecimal("50.00"));
        entry.setId(5L);
        when(cashEntryRepository.findById(5L)).thenReturn(Optional.of(entry));

        mockMvc.perform(asUserA(delete("/api/cash/5")))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_noToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/cash/1"))
                .andExpect(status().isUnauthorized());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private CashEntry cashEntry(Long userId, String type, BigDecimal amount) {
        return CashEntry.builder()
                .userId(userId).date(LocalDate.now())
                .description("Test cash").amount(amount)
                .type(type).createdAt(LocalDateTime.now()).build();
    }
}
