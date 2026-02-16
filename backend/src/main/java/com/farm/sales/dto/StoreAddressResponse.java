package com.farm.sales.dto;

import java.math.BigDecimal;

public record StoreAddressResponse(
    Long id,
    String label,
    String addressLine,
    BigDecimal latitude,
    BigDecimal longitude
) {
}
