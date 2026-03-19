package com.financeapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financeapp.model.Category;
import com.financeapp.model.MerchantCategory;
import com.financeapp.repository.CategoryRepository;
import com.financeapp.repository.MerchantCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AICategoryService {

    private final WebClient claudeWebClient;
    private final MerchantCategoryRepository merchantCategoryRepository;
    private final CategoryRepository categoryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String MODEL = "gemini-flash-latest";

    public Map<String, String> categorizeAll(List<String> descriptions) {
        if (descriptions == null || descriptions.isEmpty()) {
            return Collections.emptyMap();
        }

        // Deduplicate while preserving all original descriptions
        List<String> unique = descriptions.stream().distinct().toList();

        // Normalize each description to a merchant key
        Map<String, String> descToKey = new LinkedHashMap<>();
        for (String desc : unique) {
            descToKey.put(desc, normalizeMerchantKey(desc));
        }

        List<String> allKeys = new ArrayList<>(descToKey.values());

        // Batch-load cache
        List<MerchantCategory> cached = merchantCategoryRepository.findAllByMerchantKeyIn(allKeys);
        Map<String, String> keyToCategory = cached.stream()
                .collect(Collectors.toMap(MerchantCategory::getMerchantKey, MerchantCategory::getCategory));

        // Split into cached vs uncached
        List<String> uncachedDescs = new ArrayList<>();
        for (String desc : unique) {
            String key = descToKey.get(desc);
            if (!keyToCategory.containsKey(key)) {
                uncachedDescs.add(desc);
            }
        }

        // Call Gemini for uncached descriptions
        if (!uncachedDescs.isEmpty()) {
            try {
                Map<String, String> aiResults = callClaudeForCategories(uncachedDescs);
                Set<String> savedInBatch = new HashSet<>();
                for (Map.Entry<String, String> entry : aiResults.entrySet()) {
                    String desc = entry.getKey();
                    String category = entry.getValue();
                    String key = descToKey.get(desc);
                    if (key == null) continue;

                    keyToCategory.put(key, category);

                    // Skip if another description in this batch already saved this key
                    if (savedInBatch.contains(key)) continue;
                    savedInBatch.add(key);

                    ensureCategoryExists(category);

                    try {
                        merchantCategoryRepository.save(MerchantCategory.builder()
                                .merchantKey(key)
                                .category(category)
                                .build());
                    } catch (Exception saveEx) {
                        log.debug("Merchant key '{}' already cached, skipping save", key);
                    }
                }
            } catch (Exception e) {
                log.warn("Gemini categorization failed, falling back to 'Other' for {} descriptions: {}",
                        uncachedDescs.size(), e.getMessage());
                for (String desc : uncachedDescs) {
                    String key = descToKey.get(desc);
                    if (key != null) keyToCategory.put(key, "Other");
                }
            }
        }

        // Build result map for all original descriptions
        Map<String, String> result = new LinkedHashMap<>();
        for (String desc : descriptions) {
            String key = descToKey.get(desc);
            result.put(desc, key != null ? keyToCategory.getOrDefault(key, "Other") : "Other");
        }
        return result;
    }

    private String normalizeMerchantKey(String desc) {
        return desc.toLowerCase()
                .replaceAll("\\s*#?\\d+\\s*", " ")  // remove reference numbers
                .replaceAll("[^a-z\\s]", " ")        // keep only letters/spaces
                .replaceAll("\\s+", " ")
                .trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> callClaudeForCategories(List<String> descriptions) throws Exception {
        String prompt = buildCategorizationPrompt(descriptions);

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                        "temperature", 0,
                        "response_mime_type", "application/json"
                )
        );

        Map<?, ?> response = claudeWebClient.post()
                .uri("/v1beta/models/" + MODEL + ":generateContent")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        String text = (String) parts.get(0).get("text");

        return parseClaudeResponse(text, descriptions);
    }

    private String buildCategorizationPrompt(List<String> descriptions) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a financial transaction categorizer. For each bank transaction description below,\n");
        sb.append("identify the merchant and assign a spending category.\n\n");
        sb.append("Preferred categories: Food, Transport, Utilities, Shopping, Health, Entertainment, Transfer, Other\n");
        sb.append("You may create a new category name if none of the above fit well.\n\n");
        sb.append("Rules:\n");
        sb.append("- Use your knowledge of real merchants, businesses, and financial institutions.\n");
        sb.append("- Peer-to-peer payments (Zelle, Venmo, PayPal, CashApp) → \"Transfer\"\n");
        sb.append("- Respond ONLY with a valid JSON array. No markdown, no explanation.\n\n");
        sb.append("Format: [{\"description\": \"<exact input>\", \"category\": \"<category>\"}]\n\n");
        sb.append("Transactions:\n");
        for (int i = 0; i < descriptions.size(); i++) {
            sb.append(i + 1).append(". ").append(descriptions.get(i)).append("\n");
        }
        return sb.toString();
    }

    private Map<String, String> parseClaudeResponse(String text, List<String> descriptions) {
        Map<String, String> result = new LinkedHashMap<>();

        // Strip possible markdown fences
        String cleaned = text.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-z]*\\s*", "").replaceAll("```\\s*$", "").strip();
        }

        try {
            JsonNode array = objectMapper.readTree(cleaned);
            if (array.isArray()) {
                for (JsonNode node : array) {
                    String desc = node.has("description") ? node.get("description").asText() : null;
                    String cat = node.has("category") ? node.get("category").asText() : null;
                    if (desc != null && cat != null && !cat.isBlank()) {
                        result.put(desc, cat);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Claude response as JSON: {}", e.getMessage());
        }

        // Fill missing descriptions with "Other"
        for (String desc : descriptions) {
            result.putIfAbsent(desc, "Other");
        }
        return result;
    }

    private void ensureCategoryExists(String name) {
        if (name == null || name.isBlank()) return;
        categoryRepository.findByName(name).orElseGet(() ->
                categoryRepository.save(Category.builder()
                        .name(name)
                        .type("expense")
                        .icon("tag")
                        .color("#9ca3af")
                        .build())
        );
    }
}
