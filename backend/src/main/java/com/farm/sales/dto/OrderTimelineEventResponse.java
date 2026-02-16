package com.farm.sales.dto;

import java.time.Instant;

public record OrderTimelineEventResponse(
    Long id,
    Long orderId,
    String fromStatus,
    String toStatus,
    String actorUsername,
    Long actorUserId,
    String actorRole,
    String details,
    Instant createdAt
) {
}
