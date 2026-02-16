package com.farm.sales.dto;

import java.time.Instant;

public record StockMovementResponse(
    Long id,
    Long productId,
    String productName,
    Long orderId,
    String movementType,
    Integer quantityChange,
    String reason,
    String actorUsername,
    Long actorUserId,
    String actorRole,
    Instant createdAt
) {
}
