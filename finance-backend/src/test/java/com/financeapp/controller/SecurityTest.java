package com.financeapp.controller;

import com.financeapp.repository.CashEntryRepository;
import com.financeapp.repository.TransactionRepository;
import com.financeapp.service.ClaudeAIService;
import com.financeapp.service.GoalService;
import com.financeapp.service.StatementService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies all protected endpoints return 401 without a token,
 * and that ownership checks return 404 (don't leak resource existence).
 */
class SecurityTest extends BaseControllerTest {

    @MockitoBean GoalService goalService;
    @MockitoBean StatementService statementService;
    @MockitoBean CashEntryRepository cashEntryRepository;
    @MockitoBean TransactionRepository transactionRepository;
    @MockitoBean ClaudeAIService claudeAIService;

    // ── No token → 401 ───────────────────────────────────────────────────────

    @Test void goals_noToken_401() throws Exception {
        mockMvc.perform(get("/api/goals")).andExpect(status().isUnauthorized());
    }

    @Test void createGoal_noToken_401() throws Exception {
        mockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void statements_noToken_401() throws Exception {
        mockMvc.perform(get("/api/statements")).andExpect(status().isUnauthorized());
    }

    @Test void cash_noToken_401() throws Exception {
        mockMvc.perform(get("/api/cash")).andExpect(status().isUnauthorized());
    }

    @Test void chat_noToken_401() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void analytics_noToken_401() throws Exception {
        mockMvc.perform(get("/api/analytics/categories?from=2025-01&to=2025-03"))
                .andExpect(status().isUnauthorized());
    }

    @Test void summaries_noToken_401() throws Exception {
        mockMvc.perform(get("/api/summaries")).andExpect(status().isUnauthorized());
    }

    // ── Invalid/malformed token → 401 ────────────────────────────────────────

    @Test void goals_invalidToken_401() throws Exception {
        mockMvc.perform(get("/api/goals").header("Authorization", "Bearer garbage.token.here"))
                .andExpect(status().isUnauthorized());
    }

    @Test void goals_malformedBearer_401() throws Exception {
        mockMvc.perform(get("/api/goals").header("Authorization", "NotBearer token"))
                .andExpect(status().isUnauthorized());
    }

    @Test void goals_emptyBearer_401() throws Exception {
        mockMvc.perform(get("/api/goals").header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    // ── Cross-user ownership: 404 not 403 (don't leak existence) ─────────────

    @Test
    void userB_cannotReadUserA_goal_gets404() throws Exception {
        when(goalService.findById(eq(USER_ID_B), eq(1L)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found"));

        mockMvc.perform(asUserB(get("/api/goals/1")))
                .andExpect(status().isNotFound());
    }

    @Test
    void userB_cannotDeleteUserA_statement_gets404() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Statement not found"))
                .when(statementService).delete(USER_ID_B, 1L);

        mockMvc.perform(asUserB(delete("/api/statements/1")))
                .andExpect(status().isNotFound());
    }

    @Test
    void userB_cannotDepositIntoUserA_goal_gets404() throws Exception {
        when(goalService.deposit(eq(USER_ID_B), eq(1L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found"));

        mockMvc.perform(asUserB(post("/api/goals/1/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100}")))
                .andExpect(status().isNotFound());
    }
}
