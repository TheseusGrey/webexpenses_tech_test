package com.webexpenses.claims.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresIn
) {}
