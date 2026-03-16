package com.financeapp.service;

import com.financeapp.model.ChatHistory;
import com.financeapp.model.Goal;
import com.financeapp.model.MonthlySummary;
import com.financeapp.model.Transaction;
import com.financeapp.repository.ChatHistoryRepository;
import com.financeapp.repository.GoalRepository;
import com.financeapp.repository.MonthlySummaryRepository;
import com.financeapp.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClaudeAIService {

    private final WebClient claudeWebClient;
    private final TransactionRepository transactionRepository;
    private final GoalRepository goalRepository;
    private final MonthlySummaryRepository summaryRepository;
    private final ChatHistoryRepository chatHistoryRepository;

    private static final String MODEL = "claude-opus-4-6";
    private static final int MAX_TOKENS = 1024;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    public String chat(Long userId, String userMessage) {
        // Fetch history before saving new message (so we don't include the current turn)
        List<ChatHistory> history = chatHistoryRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
        Collections.reverse(history);

        // Save user message
        chatHistoryRepository.save(ChatHistory.builder()
                .userId(userId)
                .role(ChatHistory.Role.user)
                .message(userMessage)
                .build());

        // Build messages list from history + new user message
        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatHistory h : history) {
            messages.add(Map.of("role", h.getRole().name(), "content", h.getMessage()));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        // Build financial context system prompt
        String systemPrompt = buildSystemPrompt(userId);

        // Call Claude API
        String assistantMessage;
        try {
            assistantMessage = callClaude(systemPrompt, messages);
        } catch (WebClientResponseException e) {
            assistantMessage = "I'm having trouble connecting to the AI service. Please check your API key configuration.";
        } catch (Exception e) {
            assistantMessage = "An error occurred while processing your request. Please try again.";
        }

        // Save assistant response
        chatHistoryRepository.save(ChatHistory.builder()
                .userId(userId)
                .role(ChatHistory.Role.assistant)
                .message(assistantMessage)
                .build());

        return assistantMessage;
    }

    @SuppressWarnings("unchecked")
    private String callClaude(String systemPrompt, List<Map<String, String>> messages) {
        Map<String, Object> body = Map.of(
                "model", MODEL,
                "max_tokens", MAX_TOKENS,
                "system", systemPrompt,
                "messages", messages
        );

        Map<?, ?> response = claudeWebClient.post()
                .uri("/v1/messages")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        return (String) content.get(0).get("text");
    }

    private String buildSystemPrompt(Long userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a personal finance assistant. Be concise, helpful, and specific to the user's data.\n\n");

        List<MonthlySummary> summaries = summaryRepository.findByUserIdOrderByYearDescMonthDesc(userId);
        if (!summaries.isEmpty()) {
            sb.append("Monthly Summaries (most recent first):\n");
            for (MonthlySummary s : summaries.stream().limit(6).toList()) {
                sb.append(String.format("- %d/%d: Income $%.2f, Expenses $%.2f, Net $%.2f%n",
                        s.getMonth(), s.getYear(),
                        s.getTotalIncome(), s.getTotalExpenses(), s.getNetSavings()));
            }
            sb.append("\n");
        }

        List<Transaction> transactions = transactionRepository.findTop30ByUserIdOrderByDateDesc(userId);
        if (!transactions.isEmpty()) {
            sb.append("Recent Transactions (last 30):\n");
            for (Transaction t : transactions) {
                sb.append(String.format("- %s: %s $%.2f [%s]%s%n",
                        t.getDate().format(DATE_FMT),
                        t.getDescription(),
                        t.getAmount(),
                        t.getType().name(),
                        t.getCategory() != null ? " - " + t.getCategory() : ""));
            }
            sb.append("\n");
        }

        List<Goal> goals = goalRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (!goals.isEmpty()) {
            sb.append("Financial Goals:\n");
            for (Goal g : goals) {
                double progress = g.getTargetAmount().doubleValue() > 0
                        ? g.getCurrentAmount().doubleValue() / g.getTargetAmount().doubleValue() * 100 : 0;
                sb.append(String.format("- %s: $%.2f/$%.2f (%.0f%%) [%s]%s%n",
                        g.getTitle(),
                        g.getCurrentAmount(), g.getTargetAmount(),
                        progress, g.getStatus().name(),
                        g.getDeadline() != null ? " due " + g.getDeadline().format(DATE_FMT) : ""));
            }
        }

        if (summaries.isEmpty() && transactions.isEmpty() && goals.isEmpty()) {
            sb.append("No financial data has been uploaded yet. Encourage the user to upload bank statements to get started.");
        }

        return sb.toString();
    }
}
