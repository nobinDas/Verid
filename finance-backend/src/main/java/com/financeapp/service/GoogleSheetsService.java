package com.financeapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financeapp.dto.statement.PendingSheetItem;
import com.financeapp.dto.statement.SheetMappingAnswer;
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
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

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

    // ── Public types ──────────────────────────────────────────────────────────

    public record WriteResult(String summary, List<PendingSheetItem> unmapped) {}

    // ── Public entry points ───────────────────────────────────────────────────

    public WriteResult writeTransactionsForMonth(List<Transaction> transactions, int month, int year) {
        if (year != 2026) {
            return new WriteResult("Skipped '" + tabLabel(month, year) + "' — only 2026 is tracked", List.of());
        }
        try {
            String tabName = findTabForMonth(month, year);
            if (tabName == null) {
                return new WriteResult("No sheet tab found for " + Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year, List.of());
            }

            List<List<Object>> sheetData = readTab(tabName);
            String sheetText = formatForLLM(sheetData);
            String skill = loadSkill();

            // ── Build transaction groups ─────────────────────────────────────
            Map<String, BigDecimal>   amountByGroup = new LinkedHashMap<>();
            Map<String, String>       clsByGroup    = new LinkedHashMap<>();
            Map<String, List<String>> descsByGroup  = new LinkedHashMap<>();
            Map<String, List<Long>>   idsByGroup    = new LinkedHashMap<>();

            for (Transaction tx : transactions) {
                String cls = tx.getClassification();
                if ("TRANSFER".equals(cls) || "CC_PAYMENT".equals(cls)) continue;
                String key = tx.getCategory() + "||" + cls;
                amountByGroup.merge(key, tx.getAmount(), BigDecimal::add);
                clsByGroup.put(key, cls);
                descsByGroup.computeIfAbsent(key, k -> new ArrayList<>()).add(tx.getDescription());
                idsByGroup.computeIfAbsent(key, k -> new ArrayList<>()).add(tx.getId());
            }

            if (amountByGroup.isEmpty()) {
                return new WriteResult("Nothing to write for '" + tabName + "'", List.of());
            }

            // ── Classify groups: learned / Gemini / pending ──────────────────
            Map<String, LearnedMapping> learned = loadLearnedMappings();
            List<CellUpdate> directUpdates  = new ArrayList<>();
            List<String>     geminiKeys     = new ArrayList<>();
            List<PendingSheetItem> pending  = new ArrayList<>();

            for (String key : amountByGroup.keySet()) {
                String[] parts = key.split("\\|\\|");
                String cat = parts[0];
                List<String> descs  = descsByGroup.get(key);
                List<Long>   ids    = idsByGroup.get(key);
                BigDecimal   amount = amountByGroup.get(key);

                // Check learned mappings (keyed by normalized first description)
                String normKey = normalizeKey(descs.get(0));
                LearnedMapping lm = learned.get(normKey);

                if (lm != null) {
                    directUpdates.add(new CellUpdate(lm.amountCell(), amount.doubleValue(), lm.descCell(),
                            descs.stream().limit(5).collect(Collectors.joining(", "))));
                } else if ("Other".equalsIgnoreCase(cat)) {
                    String repDesc = descs.stream().limit(3).collect(Collectors.joining(", "));
                    pending.add(new PendingSheetItem(ids, repDesc, cat, amount));
                } else {
                    geminiKeys.add(key);
                }
            }

            // ── Gemini for non-Other, non-learned groups ─────────────────────
            List<CellUpdate> geminiUpdates = List.of();
            if (!geminiKeys.isEmpty()) {
                geminiUpdates = callGeminiForCells(sheetText, skill, amountByGroup, clsByGroup, descsByGroup, geminiKeys);
            }

            // ── Apply all updates ─────────────────────────────────────────────
            List<CellUpdate> allUpdates = new ArrayList<>(directUpdates);
            allUpdates.addAll(geminiUpdates);

            if (!allUpdates.isEmpty()) {
                applyUpdates(tabName, allUpdates, sheetData);
                autoResize(tabName);
            }

            String summary = "Updated '" + tabName + "' — " + allUpdates.size() + " group(s) written" +
                    (pending.isEmpty() ? "" : ", " + pending.size() + " pending user input");
            return new WriteResult(summary, pending);

        } catch (IOException e) {
            log.error("Google Sheets failed for {}/{}", month, year, e);
            return new WriteResult("Google Sheets error: " + e.getMessage(), List.of());
        }
    }

    public void writeReviewedTransactions(int month, int year,
                                           List<SheetMappingAnswer> answers,
                                           Map<Long, Transaction> txById) throws IOException {
        String tabName = findTabForMonth(month, year);
        if (tabName == null) {
            log.warn("No tab found for {}/{} during sheet review", month, year);
            return;
        }

        List<List<Object>> sheetData = readTab(tabName);
        Map<String, Integer> skillRows = parseSkillRows();
        Map<String, LearnedMapping> learned = loadLearnedMappings();
        List<ValueRange> ranges = new ArrayList<>();

        for (SheetMappingAnswer answer : answers) {
            List<Transaction> txs = answer.transactionIds().stream()
                    .map(txById::get).filter(Objects::nonNull).toList();
            if (txs.isEmpty()) continue;

            double totalAmount = txs.stream().mapToDouble(t -> t.getAmount().doubleValue()).sum();
            String desc = txs.stream().map(Transaction::getDescription)
                    .distinct().limit(5).collect(Collectors.joining(", "));

            // Resolve cell references
            String normKey = normalizeKey(txs.get(0).getDescription());
            LearnedMapping lm = learned.get(normKey);
            String amountCell, descCell;

            if (lm != null) {
                amountCell = lm.amountCell();
                descCell   = lm.descCell();
            } else {
                Integer row = skillRows.get(answer.categoryName().toLowerCase());
                if (row == null) {
                    log.warn("No skill row for category '{}' — skipping", answer.categoryName());
                    continue;
                }
                String[] cells = findCellsForRow(sheetData, row, answer.categoryName());
                amountCell = cells[0];
                descCell   = cells[1];

                // Save to learned mappings
                LearnedMapping newLm = new LearnedMapping(amountCell, descCell, answer.section(), answer.categoryName());
                for (Transaction tx : txs) {
                    saveLearnedMapping(normalizeKey(tx.getDescription()), newLm);
                }
            }

            // Additive write
            double existing = existingNum(sheetData, amountCell);
            ranges.add(new ValueRange()
                    .setRange("'" + tabName + "'!" + amountCell)
                    .setValues(List.of(List.of(existing + totalAmount))));

            String existingDesc = existingStr(sheetData, descCell);
            String combined = existingDesc.isEmpty() ? desc : existingDesc + ", " + desc;
            ranges.add(new ValueRange()
                    .setRange("'" + tabName + "'!" + descCell)
                    .setValues(List.of(List.of(combined))));
        }

        if (!ranges.isEmpty()) {
            sheets.spreadsheets().values()
                    .batchUpdate(spreadsheetId, new BatchUpdateValuesRequest()
                            .setValueInputOption("USER_ENTERED")
                            .setData(ranges))
                    .execute();
            autoResize(tabName);
        }
    }

    // ── Skill file ────────────────────────────────────────────────────────────

    private String loadSkill() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("sheets-skill.md")) {
            if (is == null) return "";
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Could not load sheets-skill.md: {}", e.getMessage());
            return "";
        }
    }

    private Map<String, Integer> parseSkillRows() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String line : loadSkill().split("\n")) {
            line = line.trim();
            if (line.startsWith("#") || line.isEmpty()) continue;
            String[] parts = line.split("\\|");
            if (parts.length >= 2) {
                String name   = parts[0].trim();
                String rowStr = parts[1].trim();
                if (!rowStr.equals("?") && !rowStr.isEmpty()) {
                    try { result.put(name.toLowerCase(), Integer.parseInt(rowStr)); }
                    catch (NumberFormatException ignored) {}
                }
            }
        }
        return result;
    }

    // ── Learned mappings ──────────────────────────────────────────────────────

    private record LearnedMapping(String amountCell, String descCell, String section, String categoryName) {}

    private Path learnedMappingsPath() {
        return Path.of(System.getProperty("user.home"), ".verid", "sheets-learned.json");
    }

    @SuppressWarnings("unchecked")
    private Map<String, LearnedMapping> loadLearnedMappings() {
        Path path = learnedMappingsPath();
        if (!Files.exists(path)) return new LinkedHashMap<>();
        try {
            JsonNode root = objectMapper.readTree(Files.readString(path));
            Map<String, LearnedMapping> result = new LinkedHashMap<>();
            root.fields().forEachRemaining(e -> {
                JsonNode v = e.getValue();
                result.put(e.getKey(), new LearnedMapping(
                        v.path("amountCell").asText("C1"),
                        v.path("descCell").asText("B1"),
                        v.path("section").asText(""),
                        v.path("categoryName").asText("")));
            });
            return result;
        } catch (Exception e) {
            log.warn("Failed to load learned mappings: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private void saveLearnedMapping(String key, LearnedMapping mapping) {
        try {
            Path path = learnedMappingsPath();
            Files.createDirectories(path.getParent());
            Map<String, LearnedMapping> current = loadLearnedMappings();
            current.put(key, mapping);
            Map<String, Object> data = new LinkedHashMap<>();
            current.forEach((k, v) -> {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("amountCell", v.amountCell());
                entry.put("descCell",   v.descCell());
                entry.put("section",    v.section());
                entry.put("categoryName", v.categoryName());
                data.put(k, entry);
            });
            Files.writeString(path, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data));
        } catch (Exception e) {
            log.warn("Failed to save learned mapping for '{}': {}", key, e.getMessage());
        }
    }

    private String normalizeKey(String description) {
        if (description == null || description.isBlank()) return "unknown";
        String[] words = description.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ").trim().split("\\s+");
        return Arrays.stream(words).filter(w -> w.length() > 1).limit(3).collect(Collectors.joining(" "));
    }

    // ── Tab discovery ─────────────────────────────────────────────────────────

    private String findTabForMonth(int month, int year) throws IOException {
        String full  = Month.of(month).getDisplayName(TextStyle.FULL,  Locale.ENGLISH) + " " + year;
        String short_ = Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + year;
        return sheets.spreadsheets().get(spreadsheetId).execute()
                .getSheets().stream()
                .map(s -> s.getProperties().getTitle())
                .filter(t -> t.equalsIgnoreCase(full) || t.equalsIgnoreCase(short_))
                .findFirst().orElse(null);
    }

    // ── Sheet reading ─────────────────────────────────────────────────────────

    private List<List<Object>> readTab(String tabName) throws IOException {
        ValueRange r = sheets.spreadsheets().values()
                .get(spreadsheetId, "'" + tabName + "'!A1:G120").execute();
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

    // ── Gemini cell mapping ───────────────────────────────────────────────────

    record CellUpdate(String amountCell, double amount, String descCell, String description) {}

    @SuppressWarnings("unchecked")
    private List<CellUpdate> callGeminiForCells(
            String sheetContent, String skill,
            Map<String, BigDecimal> amountByGroup,
            Map<String, String> clsByGroup,
            Map<String, List<String>> descsByGroup,
            List<String> groupKeys) {

        StringBuilder txList = new StringBuilder();
        for (String key : groupKeys) {
            String[] parts = key.split("\\|\\|");
            String cat = parts[0], cls = parts.length > 1 ? parts[1] : "";
            List<String> descs = descsByGroup.get(key);
            txList.append("- Category: ").append(cat)
                  .append(", Classification: ").append(cls)
                  .append(", Total: $").append(amountByGroup.get(key).toPlainString())
                  .append(", Descriptions: ").append(String.join("; ", descs.stream().limit(5).toList()))
                  .append("\n");
        }

        String skillSection = skill.isBlank() ? "" : """

CATEGORY → ROW MAPPING (use row numbers where given, not ?):
%s
""".formatted(skill);

        String prompt = """
You are analyzing a personal finance Google Sheet tab.
%s
SHEET CONTENT (Row: ColA=value | ColB=value | ...):
%s

TRANSACTIONS TO WRITE INTO THE SHEET:
%s

INSTRUCTIONS:
1. If the CATEGORY → ROW MAPPING has a known row number (not ?) for this transaction's category, use that row directly.
2. Otherwise study the sheet structure and find the best matching row by its label in column A.
3. For each row, determine which cell holds the Amount and which holds Description/Notes.
4. Income (INCOME / CASH_IN): place in the income section.
5. Expense (EXPENSE / CC_CHARGE / CASH_OUT): place in the expense section.
6. Do NOT target total/summary rows or formula cells — only input rows.
7. If no match, use the most logical catch-all row (Other / Miscellaneous).

Return ONLY a valid JSON array — no markdown, no explanation:
[{"amountCell":"C20","amount":1250.00,"descCell":"B20","description":"Landlord"},{"amountCell":"C43","amount":89.50,"descCell":"B43","description":"McDonald's"}]
""".formatted(skillSection, sheetContent, txList.toString());

        try {
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                    "generationConfig", Map.of("temperature", 0, "response_mime_type", "application/json"));

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

    // ── Find cells for a specific row (used during sheet review) ──────────────

    private String[] findCellsForRow(List<List<Object>> sheetData, int rowNumber, String categoryName) {
        StringBuilder context = new StringBuilder("Sheet structure (first 5 rows + target row):\n");
        for (int i = 0; i < Math.min(5, sheetData.size()); i++) {
            if (!sheetData.get(i).isEmpty()) {
                context.append("Row ").append(i + 1).append(": ");
                context.append(formatRowSimple(sheetData.get(i))).append("\n");
            }
        }
        if (rowNumber - 1 >= 0 && rowNumber - 1 < sheetData.size()) {
            context.append("Target Row ").append(rowNumber).append(" (").append(categoryName).append("): ");
            context.append(formatRowSimple(sheetData.get(rowNumber - 1))).append("\n");
        }

        String prompt = context + "\nFor row " + rowNumber + " (labeled '" + categoryName +
                "'), which column letter holds the numeric amount and which holds the text description?\n" +
                "Return only JSON: {\"amountCol\":\"C\",\"descCol\":\"B\"}";

        try {
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                    "generationConfig", Map.of("temperature", 0, "response_mime_type", "application/json"));

            @SuppressWarnings("unchecked")
            Map<?, ?> response = geminiWebClient.post()
                    .uri("/v1beta/models/gemini-flash-latest:generateContent")
                    .bodyValue(body).retrieve().bodyToMono(Map.class)
                    .block(java.time.Duration.ofSeconds(15));

            @SuppressWarnings("unchecked")
            List<?> candidates = (List<?>) response.get("candidates");
            @SuppressWarnings("unchecked")
            Map<?, ?> content = (Map<?, ?>) ((Map<?, ?>) candidates.get(0)).get("content");
            @SuppressWarnings("unchecked")
            List<?> parts = (List<?>) content.get("parts");
            String json = (String) ((Map<?, ?>) parts.get(0)).get("text");

            String cleaned = json.strip().replaceAll("```[a-z]*\\s*", "").replaceAll("```", "").strip();
            JsonNode node = objectMapper.readTree(cleaned);
            String amountCol = node.path("amountCol").asText("C");
            String descCol   = node.path("descCol").asText("B");
            return new String[]{amountCol + rowNumber, descCol + rowNumber};
        } catch (Exception e) {
            log.warn("findCellsForRow failed for row {}: {}", rowNumber, e.getMessage());
            return new String[]{"C" + rowNumber, "B" + rowNumber};
        }
    }

    private String formatRowSimple(List<Object> row) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < Math.min(row.size(), 7); j++) {
            sb.append((char) ('A' + j)).append("=").append(row.get(j)).append(" | ");
        }
        return sb.toString();
    }

    // ── Applying updates ──────────────────────────────────────────────────────

    private void applyUpdates(String tabName, List<CellUpdate> updates, List<List<Object>> sheetData) throws IOException {
        Map<String, Double> amountByCell   = new LinkedHashMap<>();
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
            ranges.add(new ValueRange()
                    .setRange("'" + tabName + "'!" + e.getKey())
                    .setValues(List.of(List.of(existing + e.getValue()))));
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
                            .setValueInputOption("USER_ENTERED").setData(ranges))
                    .execute();
        }
    }

    // ── Cell parsing helpers ──────────────────────────────────────────────────

    private List<CellUpdate> parseCellUpdates(String json) {
        List<CellUpdate> result = new ArrayList<>();
        try {
            String cleaned = json.strip().replaceAll("^```[a-z]*\\s*", "").replaceAll("```\\s*$", "").strip();
            JsonNode array = objectMapper.readTree(cleaned);
            if (array.isArray()) {
                for (JsonNode node : array) {
                    String amountCell = node.has("amountCell") ? node.get("amountCell").asText() : null;
                    double amount     = node.has("amount")     ? node.get("amount").asDouble()   : 0;
                    String descCell   = node.has("descCell")   ? node.get("descCell").asText()   : null;
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
                                new DimensionRange().setSheetId(sheetId)
                                        .setDimension("COLUMNS").setStartIndex(0).setEndIndex(7))))))
                .execute();
    }
}
