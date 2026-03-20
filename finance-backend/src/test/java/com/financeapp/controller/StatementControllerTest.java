package com.financeapp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financeapp.dto.statement.ReviewAnswer;
import com.financeapp.dto.statement.StatementResponse;
import com.financeapp.dto.statement.TransactionResponse;
import com.financeapp.service.StatementService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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

class StatementControllerTest extends BaseControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @MockitoBean StatementService statementService;

    // ── Upload ────────────────────────────────────────────────────────────────

    @Test
    void upload_pdfSuccess_returns201() throws Exception {
        StatementResponse response = sampleStatement(1L, false);
        when(statementService.upload(eq(USER_ID_A), any())).thenReturn(response);

        MockMultipartFile pdf = new MockMultipartFile("file", "statement.pdf",
                "application/pdf", "PDF content".getBytes());

        mockMvc.perform(asUserA(multipart("/api/statements/upload").file(pdf)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.processed").value(false));
    }

    @Test
    void upload_duplicateFile_returns409() throws Exception {
        when(statementService.upload(eq(USER_ID_A), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "File already uploaded"));

        MockMultipartFile pdf = new MockMultipartFile("file", "dup.pdf",
                "application/pdf", "content".getBytes());

        mockMvc.perform(asUserA(multipart("/api/statements/upload").file(pdf)))
                .andExpect(status().isConflict());
    }

    @Test
    void upload_nonPdfFile_returns400() throws Exception {
        when(statementService.upload(eq(USER_ID_A), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF files supported"));

        MockMultipartFile txt = new MockMultipartFile("file", "data.txt",
                "text/plain", "text data".getBytes());

        mockMvc.perform(asUserA(multipart("/api/statements/upload").file(txt)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_emptyFile_returns400() throws Exception {
        when(statementService.upload(eq(USER_ID_A), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty"));

        MockMultipartFile empty = new MockMultipartFile("file", "empty.pdf",
                "application/pdf", new byte[0]);

        mockMvc.perform(asUserA(multipart("/api/statements/upload").file(empty)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_noToken_returns401() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("file", "stmt.pdf",
                "application/pdf", "data".getBytes());
        mockMvc.perform(multipart("/api/statements/upload").file(pdf))
                .andExpect(status().isUnauthorized());
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @Test
    void listStatements_returnsOwnStatements() throws Exception {
        when(statementService.findAll(USER_ID_A))
                .thenReturn(List.of(sampleStatement(1L, false), sampleStatement(2L, true)));

        mockMvc.perform(asUserA(get("/api/statements")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ── Process ───────────────────────────────────────────────────────────────

    @Test
    void process_success_returns200WithProcessedTrue() throws Exception {
        StatementResponse processed = sampleStatement(1L, true);
        when(statementService.process(USER_ID_A, 1L)).thenReturn(processed);

        mockMvc.perform(asUserA(post("/api/statements/1/process")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(true));
    }

    @Test
    void process_notOwner_returns404() throws Exception {
        when(statementService.process(USER_ID_A, 99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Statement not found"));

        mockMvc.perform(asUserA(post("/api/statements/99/process")))
                .andExpect(status().isNotFound());
    }

    @Test
    void process_rebuildsMonthlyData_createsTransactions() throws Exception {
        // After processing, the statement should be marked processed=true
        StatementResponse processed = sampleStatement(1L, true);
        when(statementService.process(USER_ID_A, 1L)).thenReturn(processed);

        mockMvc.perform(asUserA(post("/api/statements/1/process")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(true))
                .andExpect(jsonPath("$.transactionCount").value(42));
    }

    // ── Get transactions ──────────────────────────────────────────────────────

    @Test
    void getTransactions_success_returnsList() throws Exception {
        List<TransactionResponse> txList = List.of(
                new TransactionResponse(1L, LocalDate.now(), "Amazon",
                        new BigDecimal("55.00"), "DEBIT", "Shopping", LocalDateTime.now()),
                new TransactionResponse(2L, LocalDate.now(), "Salary",
                        new BigDecimal("3000.00"), "CREDIT", null, LocalDateTime.now()));
        when(statementService.getTransactions(USER_ID_A, 1L)).thenReturn(txList);

        mockMvc.perform(asUserA(get("/api/statements/1/transactions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].description").value("Amazon"));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_success_returns204() throws Exception {
        doNothing().when(statementService).delete(USER_ID_A, 1L);

        mockMvc.perform(asUserA(delete("/api/statements/1")))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_notOwner_returns404() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Statement not found"))
                .when(statementService).delete(USER_ID_A, 99L);

        mockMvc.perform(asUserA(delete("/api/statements/99")))
                .andExpect(status().isNotFound());
    }

    // ── Review ────────────────────────────────────────────────────────────────

    @Test
    void review_markAsIncome_returns204() throws Exception {
        doNothing().when(statementService).review(eq(USER_ID_A), eq(1L), any());

        List<ReviewAnswer> answers = List.of(new ReviewAnswer(List.of(1L, 2L), true));

        mockMvc.perform(asUserA(post("/api/statements/1/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(answers))))
                .andExpect(status().isNoContent());
    }

    @Test
    void review_notOwner_returns404() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Statement not found"))
                .when(statementService).review(eq(USER_ID_A), eq(99L), any());

        mockMvc.perform(asUserA(post("/api/statements/99/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]")))
                .andExpect(status().isNotFound());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private StatementResponse sampleStatement(Long id, boolean processed) {
        return new StatementResponse(id, "statement.pdf", "Capital One", "1234",
                "CREDIT_CARD", 3, 2025, processed, 42, LocalDateTime.now());
    }
}
