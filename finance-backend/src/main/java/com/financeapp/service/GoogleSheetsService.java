package com.financeapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financeapp.model.Transaction;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;

@Service
@ConditionalOnBean(Sheets.class)
@Slf4j
public class GoogleSheetsService {

    private final Sheets sheets;
    private final WebClient geminiWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.google.sheet-id}")
    private String spreadsheetId;

    @Autowired
    public GoogleSheetsService(Sheets sheets, WebClient geminiWebClient) {
        this.sheets = sheets;
        this.geminiWebClient = geminiWebClient;
    }

    // ── Public entry point ────────────────────────────────────────────────────

    public String writeTransactionsForMonth(List<Transaction> transactions, int month, int year) {
        if (year != 2026) {
            return "Skipped '" + tabLabel(month, year) + "' — only 2026 is tracked";
        }
        try {
            String tabName = findTabForMonth(month, year);
            if (tabName == null) {
                return "No sheet tab found for " + Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;
            }

            List<List<Object>> sheetData = readTab(tabName);
            String sheetText = formatForLLM(sheetData);
            List<CellUpdate> updates = askGemini(sheetText, transactions);

            if (!updates.isEmpty()) {
                applyUpdates(tabName, updates, sheetData);
                autoResize(tabName);
            }

            return "Updated '" + tabName + "' — " + transactions.size() + " transaction(s)";
        } catch (IOException e) {
            log.error("Google Sheets failed for {}/{}", month, year, e);
            return "Google Sheets error: " + e.getMessage();
        }
    }

    // ── Tab discovery ─────────────────────────────────────────────────────────

    private String findTabForMonth(int month, int year) throws IOException {
        String full  = Month.of(month).getDisplayName(TextStyle.FULL,  Locale.ENGLISH) + " " + year;
        String short_ = Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + year;

        return sheets.spreadsheets().get(spreadsheetId).execute()
                .getSheets().stream()
                .map(s -> s.getProperties().getTitle())
                .filter(t -> t.equalsIgnoreCase(full) || t.equalsIgnoreCase(short_))
                .findFirst()
                .orElse(null);
    }

    // ── Sheet reading ─────────────────────────────────────────────────────────

    private List<List<Object>> readTab(String tabName) throws IOException {
        ValueRange r = sheets.spreadsheets().values()
                .get(spreadsheetId, "'" + tabName + "'!A1:G120")
                .execute();
        return r.getValues() != null ? r.getValues() : List.of();
    }

    private String formatForLLM(List<List<Object>> data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.size(); i++) {
            List<Object> row = data.get(i);
            if (row.isEmpty()) continue;
            String colA = row.get(0).toString().trim();
            if (colA.isEmpty()) continue;
            sb.append("Row ").append(i + 1).append(": ");
            for (int j = 0; j < Math.min(row.size(), 7); j++) {
                String val = row.get(j).toString().trim();
                sb.append((char) ('A' + j)).append("=").append(val.isEmpty() ? "-" : val).append(" | ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ── Gemini mapping ────────────────────────────────────────────────────────

    record CellUpdate(String amountCell, double amount, String descCell, String description) {}

    @SuppressWarnings("unchecked")
    private List<CellUpdate> askGemini(String sheetContent, List<Transaction> transactions) {
        // Group transactions — skip transfers/payments
        Map<String, BigDecimal>   amountByGroup = new LinkedHashMap<>();
        Map<String, String>       clsByGroup    = new LinkedHashMap<>();
        Map<String, List<String>> descsByGroup  = new LinkedHashMap<>();

        for (Transaction tx : transactions) {
            String cls = tx.getClassification();
            if ("TRANSFER".equals(cls) || "CC_PAYMENT".equals(cls)) continue;
            String key = tx.getCategory() + "||" + cls;
            amountByGroup.merge(key, tx.getAmount(), BigDecimal::add);
            clsByGroup.put(key, cls);
            descsByGroup.computeIfAbsent(key, k -> new ArrayList<>()).add(tx.getDescription());
        }

        if (amountByGroup.isEmpty()) return List.of();

        StringBuilder txList = new StringBuilder();
        for (Map.Entry<String, BigDecimal> e : amountByGroup.entrySet()) {
            String[] parts = e.getKey().split("\\|\\|");
            String cat = parts[0], cls = parts.length > 1 ? parts[1] : "";
            List<String> descs = descsByGroup.get(e.getKey());
            txList.append("- Category: ").append(cat)
                  .append(", Classification: ").append(cls)
                  .append(", Total: $").append(e.getValue().toPlainString())
                  .append(", Descriptions: ")
                  .append(String.join("; ", descs.stream().limit(5).toList()))
                  .append("\n");
        }

        String prompt = """
You are analyzing a personal finance Google Sheet tab.

SHEET CONTENT (Row: ColA=value | ColB=value | ...):
%s

TRANSACTIONS TO WRITE INTO THE SHEET:
%s

INSTRUCTIONS:
1. Study the sheet structure carefully — each row has a category label in column A.
2. For each transaction group, find the best matching row by its label and determine:
   - Which cell holds the "Amount" for that row (look at the column header row)
   - Which cell holds "Description / Notes" for that row
3. Income transactions (INCOME or CASH_IN classification): place in the income section.
4. Expense transactions (EXPENSE, CC_CHARGE, CASH_OUT): place in the expense section.
5. Do NOT target total/summary rows or formula cells — only input rows.
6. If no exact match, use the most logical row (e.g. Other or Miscellaneous).

Return ONLY a valid JSON array — no markdown, no explanation:
[{"amountCell":"C20","amount":1250.00,"descCell":"B20","description":"Landlord"},{"amountCell":"C43","amount":89.50,"descCell":"B43","description":"McDonald's, Subway"}]
""".formatted(sheetContent, txList.toString());

        try {
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                    "generationConfig", Map.of("temperature", 0, "response_mime_type", "application/json")
            );

            Map<?, ?> response = geminiWebClient.post()
                    .uri("/v1beta/models/gemini-flash-latest:generateContent")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(java.time.Duration.ofSeconds(30));

            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            String json = (String) parts.get(0).get("text");

            return parseCellUpdates(json);
        } catch (Exception e) {
            log.error("Gemini cell mapping failed", e);
            return List.of();
        }
    }

    private List<CellUpdate> parseCellUpdates(String json) {
        List<CellUpdate> result = new ArrayList<>();
        try {
            String cleaned = json.strip().replaceAll("^```[a-z]*\\s*", "").replaceAll("```\\s*$", "").strip();
            JsonNode array = objectMapper.readTree(cleaned);
            if (array.isArray()) {
                for (JsonNode node : array) {
                    String amountCell = node.has("amountCell") ? node.get("amountCell").asText() : null;
                    double amount     = node.has("amount")     ? node.get("amount").asDouble()    : 0;
                    String descCell   = node.has("descCell")   ? node.get("descCell").asText()    : null;
                    String desc       = node.has("description") ? node.get("description").asText() : "";
                    if (amountCell != null && amount > 0) {
                        result.add(new CellUpdate(amountCell, amount, descCell, desc));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Gemini response: {}", e.getMessage());
        }
        return result;
    }

    // ── Applying updates ──────────────────────────────────────────────────────

    private void applyUpdates(String tabName, List<CellUpdate> updates, List<List<Object>> sheetData) throws IOException {
        // Merge updates targeting the same cell
        Map<String, Double> amountByCell  = new LinkedHashMap<>();
        Map<String, String> descByDescCell = new LinkedHashMap<>();

        for (CellUpdate u : updates) {
            amountByCell.merge(u.amountCell(), u.amount(), Double::sum);
            if (u.descCell() != null && !u.description().isBlank()) {
                descByDescCell.merge(u.descCell(), u.description(), (a, b) -> a + ", " + b);
            }
        }

        List<ValueRange> ranges = new ArrayList<>();

        for (Map.Entry<String, Double> e : amountByCell.entrySet()) {
            double existing = existingNum(sheetData, e.getKey());
            double total    = existing + e.getValue();
            ranges.add(new ValueRange()
                    .setRange("'" + tabName + "'!" + e.getKey())
                    .setValues(List.of(List.of(total))));
        }

        for (Map.Entry<String, String> e : descByDescCell.entrySet()) {
            String existing = existingStr(sheetData, e.getKey());
            String combined = existing.isEmpty() ? e.getValue() : existing + ", " + e.getValue();
            ranges.add(new ValueRange()
                    .setRange("'" + tabName + "'!" + e.getKey())
                    .setValues(List.of(List.of(combined))));
        }

        if (!ranges.isEmpty()) {
            sheets.spreadsheets().values()
                    .batchUpdate(spreadsheetId, new BatchUpdateValuesRequest()
                            .setValueInputOption("USER_ENTERED")
                            .setData(ranges))
                    .execute();
        }
    }

    // ── Cell value helpers ────────────────────────────────────────────────────

    private int[] parseRef(String ref) {
        int col = 0, i = 0;
        while (i < ref.length() && Character.isLetter(ref.charAt(i)))
            col = col * 26 + (Character.toUpperCase(ref.charAt(i++)) - 'A' + 1);
        int row = Integer.parseInt(ref.substring(i)) - 1;
        return new int[]{row, col - 1};
    }

    private double existingNum(List<List<Object>> data, String ref) {
        try {
            int[] rc = parseRef(ref);
            if (rc[0] < data.size() && rc[1] < data.get(rc[0]).size()) {
                String s = data.get(rc[0]).get(rc[1]).toString().trim().replaceAll("[^\\d.]", "");
                if (!s.isEmpty()) return Double.parseDouble(s);
            }
        } catch (Exception ignored) {}
        return 0.0;
    }

    private String existingStr(List<List<Object>> data, String ref) {
        try {
            int[] rc = parseRef(ref);
            if (rc[0] < data.size() && rc[1] < data.get(rc[0]).size()) {
                String s = data.get(rc[0]).get(rc[1]).toString().trim();
                return (s.equals("-") || s.equals("0")) ? "" : s;
            }
        } catch (Exception ignored) {}
        return "";
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private String tabLabel(int month, int year) {
        return Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + year;
    }

    private void autoResize(String tabName) throws IOException {
        Integer sheetId = sheets.spreadsheets().get(spreadsheetId).execute()
                .getSheets().stream()
                .filter(s -> tabName.equals(s.getProperties().getTitle()))
                .findFirst()
                .map(s -> s.getProperties().getSheetId())
                .orElse(null);
        if (sheetId == null) return;

        sheets.spreadsheets().batchUpdate(spreadsheetId, new BatchUpdateSpreadsheetRequest()
                .setRequests(List.of(new Request().setAutoResizeDimensions(
                        new AutoResizeDimensionsRequest().setDimensions(
                                new DimensionRange()
                                        .setSheetId(sheetId)
                                        .setDimension("COLUMNS")
                                        .setStartIndex(0)
                                        .setEndIndex(7))))))
                .execute();
    }
}
