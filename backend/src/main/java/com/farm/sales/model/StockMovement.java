package com.farm.sales.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "stock_movements")
public class StockMovement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id")
  private Order order;

  @Enumerated(EnumType.STRING)
  @Column(name = "movement_type", nullable = false, length = 20)
  private StockMovementType movementType;

  @Column(name = "quantity_change", nullable = false)
  private Integer quantityChange;

  @Column(nullable = false, length = 120)
  private String reason;

  @Column(name = "actor_username", nullable = false, length = 100)
  private String actorUsername;

  @Column(name = "actor_user_id")
  private Long actorUserId;

  @Column(name = "actor_role", length = 20)
  private String actorRole;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Product getProduct() {
    return product;
  }

  public void setProduct(Product product) {
    this.product = product;
  }

  public Order getOrder() {
    return order;
  }

  public void setOrder(Order order) {
    this.order = order;
  }

  public StockMovementType getMovementType() {
    return movementType;
  }

  public void setMovementType(StockMovementType movementType) {
    this.movementType = movementType;
  }

  public Integer getQuantityChange() {
    return quantityChange;
  }

  public void setQuantityChange(Integer quantityChange) {
    this.quantityChange = quantityChange;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public String getActorUsername() {
    return actorUsername;
  }

  public void setActorUsername(String actorUsername) {
    this.actorUsername = actorUsername;
  }

  public Long getActorUserId() {
    return actorUserId;
  }

  public void setActorUserId(Long actorUserId) {
    this.actorUserId = actorUserId;
  }

  public String getActorRole() {
    return actorRole;
  }

  public void setActorRole(String actorRole) {
    this.actorRole = actorRole;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
