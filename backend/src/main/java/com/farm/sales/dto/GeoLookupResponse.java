package com.farm.sales.dto;

import java.math.BigDecimal;

public record GeoLookupResponse(
    String placeId,
    String displayName,
    BigDecimal latitude,
    BigDecimal longitude
) {
}
