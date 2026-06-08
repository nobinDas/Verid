package com.financeapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financeapp.dto.receipt.ReceiptResponse;
import com.financeapp.model.Receipt;
import com.financeapp.repository.ReceiptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final WebClient geminiWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String MODEL = "gemini-flash-latest";

    public List<ReceiptResponse> findAll(Long userId) {
        return receiptRepository
                .findByUserIdAndExpiresAtAfterOrderByPurchaseDateDesc(userId, LocalDate.now())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ReceiptResponse upload(Long userId, MultipartFile file, int retentionYears) {
        try {
            byte[] originalBytes = file.getBytes();
            byte[] compressed = zipCompress(originalBytes, file.getOriginalFilename());

            String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
            ExtractedData extracted = extractReceiptData(originalBytes, contentType);

            Receipt receipt = Receipt.builder()
                    .userId(userId)
                    .storeName(extracted.storeName())
                    .purchaseDate(extracted.purchaseDate())
                    .totalAmount(extracted.totalAmount())
                    .itemsJson(extracted.itemsJson())
                    .imageData(compressed)
                    .originalFilename(file.getOriginalFilename())
                    .contentType(contentType)
                    .retentionYears(retentionYears)
                    .expiresAt(LocalDate.now().plusYears(retentionYears))
                    .build();

            return toResponse(receiptRepository.save(receipt));
        } catch (Exception e) {
            throw new RuntimeException("Failed to process receipt: " + e.getMessage(), e);
        }
    }

    public void delete(Long userId, Long id) {
        Receipt receipt = receiptRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("Receipt not found"));
        receiptRepository.delete(receipt);
    }

    public void deleteExpired() {
        List<Receipt> expired = receiptRepository.findByExpiresAtBefore(LocalDate.now());
        if (!expired.isEmpty()) {
            receiptRepository.deleteAll(expired);
            log.info("Deleted {} expired receipts", expired.size());
        }
    }

    public String buildChatContext(Long userId) {
        List<Receipt> receipts = receiptRepository
                .findByUserIdAndExpiresAtAfterOrderByPurchaseDateDesc(userId, LocalDate.now());
        if (receipts.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        int limit = Math.min(receipts.size(), 100);
        for (int i = 0; i < limit; i++) {
            Receipt r = receipts.get(i);
            String date = r.getPurchaseDate() != null ? r.getPurchaseDate().toString() : "unknown date";
            String store = r.getStoreName() != null ? r.getStoreName() : "Unknown Store";
            String amount = r.getTotalAmount() != null ? "$" + r.getTotalAmount() : "unknown amount";
            String items = formatItems(r.getItemsJson());
            sb.append("- ").append(date).append(" | ").append(store).append(" | ").append(amount);
            if (!items.isBlank()) sb.append(" | ").append(items);
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formatItems(String itemsJson) {
        if (itemsJson == null || itemsJson.isBlank()) return "";
        try {
            JsonNode arr = objectMapper.readTree(itemsJson);
            if (!arr.isArray()) return "";
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : arr) {
                if (!sb.isEmpty()) sb.append(", ");
                String name = item.path("name").asText("");
                double price = item.path("price").asDouble(0);
                int qty = item.path("qty").asInt(1);
                sb.append(name);
                if (qty > 1) sb.append(" x").append(qty);
                if (price > 0) sb.append(" $").append(String.format("%.2f", price));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private byte[] zipCompress(byte[] data, String filename) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            String entryName = filename != null ? filename : "receipt";
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(data);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private ExtractedData extractReceiptData(byte[] imageBytes, String contentType) {
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String prompt = """
                Extract from this receipt image: store name, purchase date (YYYY-MM-DD format), \
                total amount (number only, no currency symbol), and list of items with name/price/quantity. \
                Return JSON only, no markdown: \
                {"storeName":"...","purchaseDate":"...","totalAmount":0.00,"items":[{"name":"...","price":0.00,"qty":1}]}. \
                Use null for any field you cannot read clearly.""";

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(
                        Map.of("inlineData", Map.of("mimeType", contentType, "data", base64)),
                        Map.of("text", prompt)
                ))),
                "generationConfig", Map.of("response_mime_type", "application/json")
        );

        try {
            Map<?, ?> response = geminiWebClient.post()
                    .uri("/v1beta/models/" + MODEL + ":generateContent")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            String json = (String) parts.get(0).get("text");

            // Strip markdown wrapper if present
            json = json.replaceAll("^```json\\s*", "").replaceAll("```\\s*$", "").trim();

            JsonNode node = objectMapper.readTree(json);
            String storeName = node.path("storeName").isNull() ? null : node.path("storeName").asText(null);
            LocalDate purchaseDate = null;
            if (!node.path("purchaseDate").isNull() && !node.path("purchaseDate").asText("").isBlank()) {
                try { purchaseDate = LocalDate.parse(node.path("purchaseDate").asText()); } catch (Exception ignored) {}
            }
            BigDecimal totalAmount = null;
            if (!node.path("totalAmount").isNull() && !node.path("totalAmount").asText("").isBlank()) {
                try { totalAmount = new BigDecimal(node.path("totalAmount").asText()); } catch (Exception ignored) {}
            }
            String itemsJson = node.has("items") && node.path("items").isArray()
                    ? objectMapper.writeValueAsString(node.path("items")) : null;

            return new ExtractedData(storeName, purchaseDate, totalAmount, itemsJson);
        } catch (Exception e) {
            log.warn("Failed to extract receipt data via Gemini: {}", e.getMessage());
            return new ExtractedData(null, null, null, null);
        }
    }

    private ReceiptResponse toResponse(Receipt r) {
        return new ReceiptResponse(
                r.getId(), r.getStoreName(), r.getPurchaseDate(), r.getTotalAmount(),
                r.getItemsJson(), r.getOriginalFilename(), r.getRetentionYears(),
                r.getExpiresAt(), r.getCreatedAt()
        );
    }

    private record ExtractedData(String storeName, LocalDate purchaseDate, BigDecimal totalAmount, String itemsJson) {}
}
