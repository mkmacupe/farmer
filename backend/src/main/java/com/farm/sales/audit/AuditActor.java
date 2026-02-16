package com.farm.sales.audit;

public record AuditActor(String username, Long userId, String role) {
}
