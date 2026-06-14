package com.webexpenses.claims.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectClaimRequest(
        @NotBlank(message = "Rejection reason is required")
        String reason
) {}
