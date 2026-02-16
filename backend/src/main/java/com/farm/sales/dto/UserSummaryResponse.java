package com.farm.sales.dto;

public record UserSummaryResponse(
    Long id,
    String username,
    String fullName,
    String phone,
    String legalEntityName,
    String role
) {
}
