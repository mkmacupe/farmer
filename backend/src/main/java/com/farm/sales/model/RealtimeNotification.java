package com.farm.sales.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "realtime_notifications")
public class RealtimeNotification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_type", nullable = false, length = 64)
  private String eventType;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(nullable = false, length = 2000)
  private String message;

  @Column(name = "order_id")
  private Long orderId;

  @Column(name = "order_status", length = 20)
  private String orderStatus;

  @Column(name = "target_roles", nullable = false, length = 255)
  private String targetRoles;

  @Column(name = "target_user_ids", length = 255)
  private String targetUserIds;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Long getOrderId() {
    return orderId;
  }

  public void setOrderId(Long orderId) {
    this.orderId = orderId;
  }

  public String getOrderStatus() {
    return orderStatus;
  }

  public void setOrderStatus(String orderStatus) {
    this.orderStatus = orderStatus;
  }

  public String getTargetRoles() {
    return targetRoles;
  }

  public void setTargetRoles(String targetRoles) {
    this.targetRoles = targetRoles;
  }

  public String getTargetUserIds() {
    return targetUserIds;
  }

  public void setTargetUserIds(String targetUserIds) {
    this.targetUserIds = targetUserIds;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
