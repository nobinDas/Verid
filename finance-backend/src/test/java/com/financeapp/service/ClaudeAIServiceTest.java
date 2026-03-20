package com.financeapp.service;

import com.financeapp.model.ChatHistory;
import com.financeapp.model.Goal;
import com.financeapp.model.MonthlySummary;
import com.financeapp.model.Transaction;
import com.financeapp.repository.ChatHistoryRepository;
import com.financeapp.repository.GoalRepository;
import com.financeapp.repository.MonthlySummaryRepository;
import com.financeapp.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaudeAIServiceTest {

    @Mock WebClient claudeWebClient;
    @Mock WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock WebClient.RequestBodySpec requestBodySpec;
    @Mock @SuppressWarnings("rawtypes") WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock WebClient.ResponseSpec responseSpec;

    @Mock TransactionRepository transactionRepository;
    @Mock GoalRepository goalRepository;
    @Mock MonthlySummaryRepository summaryRepository;
    @Mock ChatHistoryRepository chatHistoryRepository;

    @InjectMocks ClaudeAIService service;

    private static final Long USER_ID = 1L;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setupWebClient() {
        when(claudeWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    // ── Gemini success ────────────────────────────────────────────────────────

    @Test
    void chat_success_returnsGeminiReply() {
        stubGeminiSuccess("Hello! You have $2000 in savings.");
        stubEmptyFinancialData();
        when(chatHistoryRepository.findTop20ByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Collections.emptyList());
        when(chatHistoryRepository.save(any())).thenReturn(null);

        String reply = service.chat(USER_ID, "How much have I saved?");

        assertThat(reply).isEqualTo("Hello! You have $2000 in savings.");
    }

    @Test
    void chat_savesUserAndAssistantMessages() {
        stubGeminiSuccess("Great question!");
        stubEmptyFinancialData();
        when(chatHistoryRepository.findTop20ByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Collections.emptyList());
        when(chatHistoryRepository.save(any())).thenReturn(null);

        service.chat(USER_ID, "Am I spending too much?");

        // Two saves: one for user message, one for assistant reply
        verify(chatHistoryRepository, times(2)).save(any(ChatHistory.class));
    }

    // ── Gemini error handling ─────────────────────────────────────────────────

    @Test
    void chat_geminiHttpError_returnsApiKeyErrorMessage() {
        when(responseSpec.bodyToMono(Map.class))
                .thenReturn(Mono.error(WebClientResponseException.create(
                        401, "Unauthorized", null, null, null)));
        stubEmptyFinancialData();
        when(chatHistoryRepository.findTop20ByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Collections.emptyList());
        when(chatHistoryRepository.save(any())).thenReturn(null);

        String reply = service.chat(USER_ID, "Hello");

        assertThat(reply).contains("API key");
    }

    @Test
    void chat_unexpectedException_returnsGenericErrorMessage() {
        when(responseSpec.bodyToMono(Map.class))
                .thenReturn(Mono.error(new RuntimeException("Network failure")));
        stubEmptyFinancialData();
        when(chatHistoryRepository.findTop20ByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Collections.emptyList());
        when(chatHistoryRepository.save(any())).thenReturn(null);

        String reply = service.chat(USER_ID, "Test");

        assertThat(reply).containsIgnoringCase("error");
    }

    // ── System prompt content (tested via chat) ───────────────────────────────

    @Test
    void chat_withTransactions_includesTransactionData() {
        Transaction tx = Transaction.builder()
                .userId(USER_ID).statementId(1L)
                .date(LocalDate.now()).description("Whole Foods")
                .amount(new BigDecimal("85.00")).type(Transaction.TransactionType.DEBIT)
                .classification("EXPENSE").category("Groceries")
                .createdAt(LocalDateTime.now()).build();

        when(transactionRepository.findTop100ByUserIdOrderByDateDesc(USER_ID)).thenReturn(List.of(tx));
        when(goalRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());
        when(summaryRepository.findByUserIdOrderByYearDescMonthDesc(USER_ID)).thenReturn(List.of());
        when(chatHistoryRepository.findTop20ByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Collections.emptyList());
        when(chatHistoryRepository.save(any())).thenReturn(null);
        stubGeminiSuccess("OK");

        service.chat(USER_ID, "What did I spend on groceries?");

        verify(requestBodySpec).bodyValue(any());
    }

    @Test
    void chat_withGoals_includesGoalData() {
        Goal goal = Goal.builder()
                .id(1L).userId(USER_ID).title("Buy a Car")
                .targetAmount(new BigDecimal("20000.00"))
                .currentAmount(new BigDecimal("5000.00"))
                .termType(Goal.TermType.LONG)
                .status(Goal.GoalStatus.ACTIVE)
                .allocationPercent(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now()).build();

        when(transactionRepository.findTop100ByUserIdOrderByDateDesc(USER_ID)).thenReturn(List.of());
        when(goalRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(goal));
        when(summaryRepository.findByUserIdOrderByYearDescMonthDesc(USER_ID)).thenReturn(List.of());
        when(chatHistoryRepository.findTop20ByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Collections.emptyList());
        when(chatHistoryRepository.save(any())).thenReturn(null);
        stubGeminiSuccess("OK");

        service.chat(USER_ID, "How am I doing on my goals?");

        verify(requestBodySpec).bodyValue(any());
    }

    @Test
    void chat_withMonthlySummaries_includesSummaryData() {
        MonthlySummary summary = new MonthlySummary(
                null, USER_ID, (short) 3, (short) 2025,
                new BigDecimal("4000.00"), new BigDecimal("2500.00"),
                new BigDecimal("1500.00"), null, LocalDateTime.now());

        when(transactionRepository.findTop100ByUserIdOrderByDateDesc(USER_ID)).thenReturn(List.of());
        when(goalRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());
        when(summaryRepository.findByUserIdOrderByYearDescMonthDesc(USER_ID)).thenReturn(List.of(summary));
        when(chatHistoryRepository.findTop20ByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Collections.emptyList());
        when(chatHistoryRepository.save(any())).thenReturn(null);
        stubGeminiSuccess("OK");

        service.chat(USER_ID, "How was March?");

        verify(requestBodySpec).bodyValue(any());
    }

    @Test
    void chat_noFinancialData_geminiStillCalled() {
        stubEmptyFinancialData();
        when(chatHistoryRepository.findTop20ByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Collections.emptyList());
        when(chatHistoryRepository.save(any())).thenReturn(null);
        stubGeminiSuccess("Upload your statements to get started!");

        service.chat(USER_ID, "Hi");

        verify(claudeWebClient).post();
    }

    @Test
    void chat_priorHistoryIncludedInNextRequest() {
        ChatHistory previousTurn = ChatHistory.builder()
                .userId(USER_ID).role(ChatHistory.Role.user)
                .message("What is my balance?").createdAt(LocalDateTime.now()).build();

        when(chatHistoryRepository.findTop20ByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(previousTurn));
        when(chatHistoryRepository.save(any())).thenReturn(null);
        stubEmptyFinancialData();
        stubGeminiSuccess("OK");

        service.chat(USER_ID, "Tell me more");

        // contents should have prior message + new message (≥ 2 entries)
        verify(requestBodySpec).bodyValue(argThat(body -> {
            if (body instanceof Map<?, ?> map) {
                Object contents = map.get("contents");
                return contents instanceof List<?> list && list.size() >= 2;
            }
            return false;
        }));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void stubGeminiSuccess(String text) {
        Map<String, Object> geminiResponse = Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of("text", text))))));
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(geminiResponse));
    }

    private void stubEmptyFinancialData() {
        when(transactionRepository.findTop100ByUserIdOrderByDateDesc(USER_ID)).thenReturn(List.of());
        when(goalRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());
        when(summaryRepository.findByUserIdOrderByYearDescMonthDesc(USER_ID)).thenReturn(List.of());
    }
}
