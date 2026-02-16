package com.farm.sales.dto;

import java.time.Instant;

public record AuditLogResponse(
    Long id,
    String actionType,
    String entityType,
    String entityId,
    String actorUsername,
    Long actorUserId,
    String actorRole,
    String details,
    Instant createdAt
) {
}
