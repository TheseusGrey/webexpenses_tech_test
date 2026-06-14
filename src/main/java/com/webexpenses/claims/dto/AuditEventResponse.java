package com.webexpenses.claims.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        UUID claimId,
        String action,
        String performedBy,
        Instant performedAt,
        String details
) {}
