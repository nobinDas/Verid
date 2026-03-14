package com.financeapp.service;

import com.financeapp.service.parser.CapitalOneParser;
import com.financeapp.service.parser.ParsedStatement;
import com.financeapp.service.parser.RegionsParser;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class StatementParserService {

    private static final Logger log = LoggerFactory.getLogger(StatementParserService.class);

    public ParsedStatement parse(byte[] pdfBytes) {
        String text = extractText(pdfBytes);
        log.info("=== PDF EXTRACTED TEXT (first 2000 chars) ===\n{}", text.substring(0, Math.min(2000, text.length())));
        log.info("=== END EXTRACTED TEXT ===");

        // Restrict bank detection to the header block (first 50 lines) so that
        // transaction descriptions like "Capital One  Crcardpmt" in a Regions
        // statement don't accidentally trigger the wrong parser.
        String[] allLines = text.split("\\r?\\n");
        int headerEnd = Math.min(50, allLines.length);
        StringBuilder header = new StringBuilder();
        for (int i = 0; i < headerEnd; i++) header.append(allLines[i]).append('\n');
        String headerLower = header.toString().toLowerCase();

        // Check Regions FIRST using header-only signals — "Capital One" can appear
        // in Regions transaction descriptions, so rule out Regions before scanning
        // the full document for Capital One.
        if (headerLower.contains("regions") || text.contains("ACCOUNT #")) {
            return new RegionsParser().parse(text);
        }
        // Capital One: check full text — the brand name and capitalone.com may appear
        // well past the first 50 lines (payment address, footer, back-of-statement).
        String textLower = text.toLowerCase();
        if (textLower.contains("capital one") || textLower.contains("capitalone.com")) {
            return new CapitalOneParser().parse(text);
        }
        throw new IllegalArgumentException("Unsupported bank — could not detect Capital One or Regions");
    }

    private String extractText(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper s = new PDFTextStripper();
            s.setSortByPosition(true);
            return s.getText(doc);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read PDF: " + e.getMessage(), e);
        }
    }
}
