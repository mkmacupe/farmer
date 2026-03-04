package com.farm.sales.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class OrderStatusConverter implements AttributeConverter<OrderStatus, String> {
  @Override
  public String convertToDatabaseColumn(OrderStatus attribute) {
    OrderStatus status = attribute == null ? OrderStatus.CREATED : attribute;
    return status.name();
  }

  @Override
  public OrderStatus convertToEntityAttribute(String dbData) {
    return OrderStatus.fromDatabase(dbData);
  }
}
