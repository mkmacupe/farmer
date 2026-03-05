package com.farm.sales.dto;

import java.math.BigDecimal;

public record ProductResponse(
    Long id,
    String name,
    String category,
    String description,
    String photoUrl,
    BigDecimal price,
    Integer stockQuantity,
    Double weightKg,
    Double volumeM3
) {
}
