package com.farm.sales.dto;

import java.util.List;

public record ProductPageResponse(
    List<ProductResponse> items,
    int page,
    int size,
    long totalItems,
    int totalPages,
    boolean hasNext
) {
}
