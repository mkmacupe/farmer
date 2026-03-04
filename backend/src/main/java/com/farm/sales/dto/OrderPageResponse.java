package com.farm.sales.dto;

import java.util.List;

public record OrderPageResponse(
    List<OrderResponse> items,
    int page,
    int size,
    long totalItems,
    int totalPages,
    boolean hasNext
) {
}
