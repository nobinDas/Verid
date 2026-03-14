package com.financeapp.dto.auth;

public record AuthResponse(
        String token,
        String email,
        String name
) {}
