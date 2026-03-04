package com.farm.sales.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderStatusConverterTest {
  private final OrderStatusConverter converter = new OrderStatusConverter();

  @Test
  void convertToEntityAttributeMapsCanonicalStatuses() {
    assertThat(converter.convertToEntityAttribute("CREATED")).isEqualTo(OrderStatus.CREATED);
    assertThat(converter.convertToEntityAttribute("APPROVED")).isEqualTo(OrderStatus.APPROVED);
    assertThat(converter.convertToEntityAttribute("ASSIGNED")).isEqualTo(OrderStatus.ASSIGNED);
    assertThat(converter.convertToEntityAttribute("DELIVERED")).isEqualTo(OrderStatus.DELIVERED);
  }

  @Test
  void convertToEntityAttributeMapsLegacyStatusesAndDefaultsSafely() {
    assertThat(converter.convertToEntityAttribute("pending")).isEqualTo(OrderStatus.CREATED);
    assertThat(converter.convertToEntityAttribute("confirmed")).isEqualTo(OrderStatus.APPROVED);
    assertThat(converter.convertToEntityAttribute("in_transit")).isEqualTo(OrderStatus.ASSIGNED);
    assertThat(converter.convertToEntityAttribute("completed")).isEqualTo(OrderStatus.DELIVERED);
    assertThat(converter.convertToEntityAttribute("unexpected")).isEqualTo(OrderStatus.CREATED);
    assertThat(converter.convertToEntityAttribute(null)).isEqualTo(OrderStatus.CREATED);
    assertThat(converter.convertToEntityAttribute("   ")).isEqualTo(OrderStatus.CREATED);
  }

  @Test
  void convertToDatabaseColumnNeverReturnsNull() {
    assertThat(converter.convertToDatabaseColumn(OrderStatus.APPROVED)).isEqualTo("APPROVED");
    assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("CREATED");
  }
}
