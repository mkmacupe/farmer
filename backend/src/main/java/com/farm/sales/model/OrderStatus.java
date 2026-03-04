package com.farm.sales.model;

import java.util.Locale;
import java.util.Map;

public enum OrderStatus {
  CREATED,
  APPROVED,
  ASSIGNED,
  DELIVERED;

  private static final Map<String, OrderStatus> LEGACY_ALIASES = Map.ofEntries(
      Map.entry("NEW", CREATED),
      Map.entry("PENDING", CREATED),
      Map.entry("PENDING_APPROVAL", CREATED),
      Map.entry("DRAFT", CREATED),
      Map.entry("OPEN", CREATED),
      Map.entry("CONFIRMED", APPROVED),
      Map.entry("ACCEPTED", APPROVED),
      Map.entry("IN_DELIVERY", ASSIGNED),
      Map.entry("IN_TRANSIT", ASSIGNED),
      Map.entry("ON_ROUTE", ASSIGNED),
      Map.entry("SHIPPED", ASSIGNED),
      Map.entry("DONE", DELIVERED),
      Map.entry("COMPLETED", DELIVERED),
      Map.entry("CLOSED", DELIVERED)
  );

  public static OrderStatus fromDatabase(String rawValue) {
    if (rawValue == null || rawValue.isBlank()) {
      return CREATED;
    }
    String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
    try {
      return OrderStatus.valueOf(normalized);
    } catch (IllegalArgumentException ignored) {
      return LEGACY_ALIASES.getOrDefault(normalized, CREATED);
    }
  }
}
