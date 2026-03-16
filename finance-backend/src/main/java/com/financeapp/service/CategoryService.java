package com.financeapp.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CategoryService {

    private static final Map<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();

    static {
        CATEGORY_KEYWORDS.put("Food", List.of(
                "restaurant", "mcdonald", "subway", "chipotle", "starbucks", "coffee",
                "pizza", "burger", "taco bell", "doordash", "grubhub", "uber eats",
                "dining", "kitchen", "cafe", "bakery", "sushi", "grocery", "kroger",
                "publix", "whole foods", "trader joe", "aldi", "panera", "chick-fil",
                "wendy", "kfc", "popeyes", "waffle house", "denny", "ihop"
        ));
        CATEGORY_KEYWORDS.put("Transport", List.of(
                "uber", "lyft", "taxi", "shell", "exxon", "chevron", "bp oil",
                "sunoco", "citgo", "marathon", "parking", "toll", "transit", "metro",
                "mta", "amtrak", "delta air", "united air", "american air", "southwest air",
                "spirit air", "enterprise rent", "hertz", "avis", "sunpass", "fastrak", "gas station"
        ));
        CATEGORY_KEYWORDS.put("Utilities", List.of(
                "electric", "water bill", "internet", "comcast", "xfinity", "at&t",
                "verizon", "t-mobile", "phone bill", "duke energy", "utility",
                "spectrum", "directv", "cox comm", "dish network"
        ));
        CATEGORY_KEYWORDS.put("Shopping", List.of(
                "amazon", "walmart", "target", "costco", "ebay", "etsy", "best buy",
                "home depot", "lowe's", "ikea", "zara", "h&m", "nordstrom", "macy",
                "tj maxx", "marshall", "ross store", "apple store", "gap ", "old navy",
                "bath body", "victoria secret"
        ));
        CATEGORY_KEYWORDS.put("Health", List.of(
                "pharmacy", "cvs", "walgreen", "rite aid", "hospital", "clinic",
                "doctor", "dental", "vision", "planet fitness", "la fitness",
                "medical", "insurance", "urgent care", "optometrist", "gym "
        ));
        CATEGORY_KEYWORDS.put("Entertainment", List.of(
                "netflix", "spotify", "hulu", "disney+", "hbo", "apple tv",
                "amazon prime", "youtube", "steam", "playstation", "xbox", "movie",
                "amc theater", "concert", "ticketmaster", "eventbrite", "gaming"
        ));
        CATEGORY_KEYWORDS.put("Transfer", List.of(
                "zelle", "venmo", "paypal", "cashapp", "cash app",
                "wire transfer", "online transfer", "peer payment", "mobile transfer"
        ));
    }

    public String categorize(String description) {
        if (description == null) return "Other";
        String lower = description.toLowerCase();
        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return "Other";
    }
}
