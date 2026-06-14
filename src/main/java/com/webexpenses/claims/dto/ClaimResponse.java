package com.webexpenses.claims.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ClaimResponse(
        UUID id,
        UUID userId,
        String description,
        BigDecimal amount,
        LocalDate expenseDate,
        String category,
        String status,
        String rejectionReason,
        Instant createdAt
) {}
