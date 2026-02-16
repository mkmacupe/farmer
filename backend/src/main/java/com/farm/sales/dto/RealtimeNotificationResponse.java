package com.farm.sales.dto;

import java.time.Instant;

public record RealtimeNotificationResponse(
    String type,
    String title,
    String message,
    Long orderId,
    String status,
    Instant createdAt
) {
}
