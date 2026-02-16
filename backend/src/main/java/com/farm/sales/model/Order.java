package com.farm.sales.model;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id", nullable = false)
  private User customer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "delivery_address_id", nullable = false)
  private StoreAddress deliveryAddress;

  @Column(name = "delivery_address_text", nullable = false)
  private String deliveryAddressText;

  @Column(name = "delivery_latitude", precision = 10, scale = 7)
  private BigDecimal deliveryLatitude;

  @Column(name = "delivery_longitude", precision = 10, scale = 7)
  private BigDecimal deliveryLongitude;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrderStatus status;

  @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
  private BigDecimal totalAmount;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "approved_by_manager_id")
  private User approvedByManager;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assigned_driver_id")
  private User assignedDriver;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assigned_by_logistician_id")
  private User assignedByLogistician;

  @Column(name = "assigned_at")
  private Instant assignedAt;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<OrderItem> items = new ArrayList<>();

  public Order() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public User getCustomer() {
    return customer;
  }

  public void setCustomer(User customer) {
    this.customer = customer;
  }

  public StoreAddress getDeliveryAddress() {
    return deliveryAddress;
  }

  public void setDeliveryAddress(StoreAddress deliveryAddress) {
    this.deliveryAddress = deliveryAddress;
  }

  public String getDeliveryAddressText() {
    return deliveryAddressText;
  }

  public void setDeliveryAddressText(String deliveryAddressText) {
    this.deliveryAddressText = deliveryAddressText;
  }

  public BigDecimal getDeliveryLatitude() {
    return deliveryLatitude;
  }

  public void setDeliveryLatitude(BigDecimal deliveryLatitude) {
    this.deliveryLatitude = deliveryLatitude;
  }

  public BigDecimal getDeliveryLongitude() {
    return deliveryLongitude;
  }

  public void setDeliveryLongitude(BigDecimal deliveryLongitude) {
    this.deliveryLongitude = deliveryLongitude;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public void setStatus(OrderStatus status) {
    this.status = status;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public void setTotalAmount(BigDecimal totalAmount) {
    this.totalAmount = totalAmount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public User getApprovedByManager() {
    return approvedByManager;
  }

  public void setApprovedByManager(User approvedByManager) {
    this.approvedByManager = approvedByManager;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public void setApprovedAt(Instant approvedAt) {
    this.approvedAt = approvedAt;
  }

  public User getAssignedDriver() {
    return assignedDriver;
  }

  public void setAssignedDriver(User assignedDriver) {
    this.assignedDriver = assignedDriver;
  }

  public User getAssignedByLogistician() {
    return assignedByLogistician;
  }

  public void setAssignedByLogistician(User assignedByLogistician) {
    this.assignedByLogistician = assignedByLogistician;
  }

  public Instant getAssignedAt() {
    return assignedAt;
  }

  public void setAssignedAt(Instant assignedAt) {
    this.assignedAt = assignedAt;
  }

  public Instant getDeliveredAt() {
    return deliveredAt;
  }

  public void setDeliveredAt(Instant deliveredAt) {
    this.deliveredAt = deliveredAt;
  }

  public List<OrderItem> getItems() {
    return items;
  }

  public void setItems(List<OrderItem> items) {
    this.items = items;
  }
}
