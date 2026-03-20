package com.financeapp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financeapp.dto.goal.CreateGoalRequest;
import com.financeapp.dto.goal.DepositRequest;
import com.financeapp.dto.goal.GoalResponse;
import com.financeapp.dto.goal.UpdateGoalRequest;
import com.financeapp.model.Goal;
import com.financeapp.service.GoalService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GoalControllerTest extends BaseControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @MockitoBean GoalService goalService;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @Test
    void createGoal_success_returns201() throws Exception {
        GoalResponse response = buildGoalResponse(1L);
        when(goalService.create(eq(USER_ID_A), any())).thenReturn(response);

        mockMvc.perform(asUserA(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGoalRequest("Emergency Fund", "3 months",
                                        new BigDecimal("5000.00"), Goal.TermType.MID,
                                        null, new BigDecimal("20.00"))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Emergency Fund"))
                .andExpect(jsonPath("$.targetAmount").value(5000.00));
    }

    @Test
    void createGoal_missingTitle_returns400() throws Exception {
        mockMvc.perform(asUserA(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAmount\":1000,\"termType\":\"SHORT\"}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createGoal_missingTargetAmount_returns400() throws Exception {
        mockMvc.perform(asUserA(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"My Goal\",\"termType\":\"SHORT\"}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listGoals_success_returnsOwnGoals() throws Exception {
        when(goalService.findAll(USER_ID_A))
                .thenReturn(List.of(buildGoalResponse(1L), buildGoalResponse(2L)));

        mockMvc.perform(asUserA(get("/api/goals")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listGoals_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/goals"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getGoal_success_returns200() throws Exception {
        when(goalService.findById(USER_ID_A, 1L)).thenReturn(buildGoalResponse(1L));

        mockMvc.perform(asUserA(get("/api/goals/1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getGoal_notFound_returns404() throws Exception {
        when(goalService.findById(USER_ID_A, 99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found"));

        mockMvc.perform(asUserA(get("/api/goals/99")))
                .andExpect(status().isNotFound());
    }

    @Test
    void getGoal_otherUserGoal_returns404() throws Exception {
        // User B tries to get User A's goal — service returns 404 (ownership check)
        when(goalService.findById(USER_ID_B, 1L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found"));

        mockMvc.perform(asUserB(get("/api/goals/1")))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateGoal_partialFields_returns200() throws Exception {
        GoalResponse updated = buildGoalResponse(1L);
        when(goalService.update(eq(USER_ID_A), eq(1L), any())).thenReturn(updated);

        mockMvc.perform(asUserA(put("/api/goals/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateGoalRequest("Updated Title", null, null, null, null, null, null)))))
                .andExpect(status().isOk());
    }

    @Test
    void deleteGoal_success_returns204() throws Exception {
        doNothing().when(goalService).delete(USER_ID_A, 1L);

        mockMvc.perform(asUserA(delete("/api/goals/1")))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteGoal_otherUserGoal_returns404() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found"))
                .when(goalService).delete(USER_ID_B, 1L);

        mockMvc.perform(asUserB(delete("/api/goals/1")))
                .andExpect(status().isNotFound());
    }

    // ── Deposit ───────────────────────────────────────────────────────────────

    @Test
    void deposit_success_returns200() throws Exception {
        when(goalService.deposit(eq(USER_ID_A), eq(1L), any())).thenReturn(buildGoalResponse(1L));

        mockMvc.perform(asUserA(post("/api/goals/1/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DepositRequest(new BigDecimal("100.00"))))))
                .andExpect(status().isOk());
    }

    @Test
    void deposit_zeroAmount_returns400() throws Exception {
        // @DecimalMin("0.01") — 0.00 fails validation
        mockMvc.perform(asUserA(post("/api/goals/1/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DepositRequest(BigDecimal.ZERO)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deposit_negativeAmount_returns400() throws Exception {
        mockMvc.perform(asUserA(post("/api/goals/1/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DepositRequest(new BigDecimal("-50.00"))))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deposit_exceedsAvailableSavings_returns400() throws Exception {
        when(goalService.deposit(eq(USER_ID_A), eq(1L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount exceeds available savings"));

        mockMvc.perform(asUserA(post("/api/goals/1/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DepositRequest(new BigDecimal("99999.00"))))))
                .andExpect(status().isBadRequest());
    }

    // ── Savings summary ───────────────────────────────────────────────────────

    @Test
    void getSavingsSummary_returns200() throws Exception {
        GoalService.SavingsSummary summary = new GoalService.SavingsSummary(
                new BigDecimal("2000.00"), new BigDecimal("500.00"), new BigDecimal("1500.00"));
        when(goalService.getSavingsSummary(USER_ID_A)).thenReturn(summary);

        mockMvc.perform(asUserA(get("/api/goals/savings-summary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSaving").value(2000.00))
                .andExpect(jsonPath("$.availableSaving").value(1500.00));
    }

    @Test
    void getSavingsSummary_noData_returnsZeros() throws Exception {
        GoalService.SavingsSummary summary = new GoalService.SavingsSummary(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        when(goalService.getSavingsSummary(USER_ID_A)).thenReturn(summary);

        mockMvc.perform(asUserA(get("/api/goals/savings-summary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSaving").value(0))
                .andExpect(jsonPath("$.availableSaving").value(0));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private GoalResponse buildGoalResponse(Long id) {
        return new GoalResponse(id, "Emergency Fund", "Description",
                new BigDecimal("5000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                Goal.TermType.MID, LocalDate.now().plusYears(1),
                Goal.GoalStatus.ACTIVE, new BigDecimal("20.00"),
                LocalDateTime.now(), false);
    }
}
