package com.financeapp.controller;

import com.financeapp.dto.chat.ChatRequest;
import com.financeapp.dto.chat.ChatResponse;
import com.financeapp.model.User;
import com.financeapp.service.ClaudeAIService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ClaudeAIService claudeAIService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        Long userId = currentUserId();
        String reply = claudeAIService.chat(userId, request.message());
        return ResponseEntity.ok(new ChatResponse(reply));
    }

    private Long currentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
