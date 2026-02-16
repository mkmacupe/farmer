package com.farm.sales.dto;

public record AuthResponse(
    String token,
    String username,
    String fullName,
    String role
) {
}
