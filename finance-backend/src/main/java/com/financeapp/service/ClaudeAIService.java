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

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class ClaudeAIService {

    private final WebClient geminiWebClient;
    private final TransactionRepository transactionRepository;
    private final GoalRepository goalRepository;
    private final MonthlySummaryRepository summaryRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final ReceiptService receiptService;

    private static final String MODEL = "gemini-flash-latest";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    public String chat(Long userId, String userMessage) {
        List<ChatHistory> history = chatHistoryRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
        Collections.reverse(history);

        chatHistoryRepository.save(ChatHistory.builder()
                .userId(userId)
                .role(ChatHistory.Role.user)
                .message(userMessage)
                .build());

        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatHistory h : history) {
            messages.add(Map.of("role", h.getRole().name(), "content", h.getMessage()));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        String systemPrompt = buildSystemPrompt(userId);

        String assistantMessage;
        try {
            assistantMessage = callGemini(systemPrompt, messages);
        } catch (WebClientResponseException e) {
            assistantMessage = "I'm having trouble connecting to the AI service. Please check your API key configuration.";
        } catch (Exception e) {
            assistantMessage = "An error occurred while processing your request. Please try again.";
        }

        chatHistoryRepository.save(ChatHistory.builder()
                .userId(userId)
                .role(ChatHistory.Role.assistant)
                .message(assistantMessage)
                .build());

        return assistantMessage;
    }

    @SuppressWarnings("unchecked")
    private String callGemini(String systemPrompt, List<Map<String, String>> messages) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (Map<String, String> msg : messages) {
            String role = "assistant".equals(msg.get("role")) ? "model" : "user";
            contents.add(Map.of("role", role, "parts", List.of(Map.of("text", msg.get("content")))));
        }

        Map<String, Object> body = Map.of(
                "contents", contents,
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt)))
        );

        Map<?, ?> response = geminiWebClient.post()
                .uri("/v1beta/models/" + MODEL + ":generateContent")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        return (String) parts.get(0).get("text");
    }

    private String buildSystemPrompt(Long userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are a personal finance assistant for the Verid app.

                STRICT RULES:
                - You ONLY answer questions about personal finance: spending, income, savings, budgets, goals, transactions, and financial advice.
                - If asked about ANYTHING unrelated to finance (sports, weather, coding, general knowledge, etc.), respond EXACTLY: "That's outside my area of expertise. I can only help with questions about your finances, spending, savings, and financial goals."
                - Be specific to the user's actual data shown below. Do not make up numbers.
                - Be concise and actionable.

                """);

        List<MonthlySummary> summaries = summaryRepository.findByUserIdOrderByYearDescMonthDesc(userId);
        if (!summaries.isEmpty()) {
            sb.append("Monthly Summaries (most recent first):\n");
            for (MonthlySummary s : summaries) {
                sb.append(String.format("- %d/%d: Income $%.2f, Expenses $%.2f, Net $%.2f%n",
                        s.getMonth(), s.getYear(),
                        s.getTotalIncome(), s.getTotalExpenses(), s.getNetSavings()));
            }
            sb.append("\n");
        }

        List<Transaction> transactions = transactionRepository.findTop100ByUserIdOrderByDateDesc(userId);
        if (!transactions.isEmpty()) {
            Map<String, BigDecimal> categoryTotals = new TreeMap<>();
            for (Transaction t : transactions) {
                if (t.getType() != null && "debit".equalsIgnoreCase(t.getType().name())) {
                    String cat = t.getCategory() != null ? t.getCategory() : "Uncategorized";
                    categoryTotals.merge(cat, t.getAmount(), BigDecimal::add);
                }
            }
            if (!categoryTotals.isEmpty()) {
                sb.append("Spending by Category (from last 100 transactions):\n");
                categoryTotals.entrySet().stream()
                        .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                        .forEach(e -> sb.append(String.format("- %s: $%.2f%n", e.getKey(), e.getValue())));
                sb.append("\n");
            }

            sb.append("Recent Transactions (last 100):\n");
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

        String receiptCtx = receiptService.buildChatContext(userId);
        if (!receiptCtx.isBlank()) {
            sb.append("\n\nSaved Receipts (scanned by user):\n").append(receiptCtx);
        }

        if (summaries.isEmpty() && transactions.isEmpty() && goals.isEmpty() && receiptCtx.isBlank()) {
            sb.append("No financial data has been uploaded yet. Encourage the user to upload bank statements to get started.");
        }

        return sb.toString();
    }
}
