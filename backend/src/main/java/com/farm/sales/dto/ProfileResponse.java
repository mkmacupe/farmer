package com.farm.sales.dto;

public record ProfileResponse(
    Long id,
    String username,
    String fullName,
    String phone,
    String legalEntityName
) {
}
