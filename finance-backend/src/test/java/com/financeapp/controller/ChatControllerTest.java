package com.financeapp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financeapp.dto.chat.ChatRequest;
import com.financeapp.service.ClaudeAIService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChatControllerTest extends BaseControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @MockitoBean ClaudeAIService claudeAIService;

    @Test
    void chat_success_returns200WithReply() throws Exception {
        when(claudeAIService.chat(eq(USER_ID_A), any()))
                .thenReturn("You spent $500 on food last month.");

        mockMvc.perform(asUserA(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("How much did I spend?")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("You spent $500 on food last month."));
    }

    @Test
    void chat_replyIsNonEmpty() throws Exception {
        when(claudeAIService.chat(eq(USER_ID_A), any()))
                .thenReturn("Here is your financial summary...");

        mockMvc.perform(asUserA(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("Summarize my finances")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").isNotEmpty());
    }

    @Test
    void chat_delegatesUserIdToService() throws Exception {
        when(claudeAIService.chat(eq(USER_ID_A), eq("My message"))).thenReturn("OK");

        mockMvc.perform(asUserA(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("My message")))))
                .andExpect(status().isOk());

        verify(claudeAIService).chat(USER_ID_A, "My message");
    }

    @Test
    void chat_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("Hello"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chat_invalidToken_returns401() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .header("Authorization", "Bearer invalid-garbage-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("Hello"))))
                .andExpect(status().isUnauthorized());
    }
}
