package com.farm.sales.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String category;

  @Column
  private String description;

  @Column(name = "photo_url")
  private String photoUrl;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal price;

  @Column(name = "stock_quantity", nullable = false)
  private Integer stockQuantity;

  @Column(name = "weight_kg", nullable = false)
  private Double weightKg = 1.0;

  @Column(name = "volume_m3", nullable = false)
  private Double volumeM3 = 0.001;

  public Product() {
  }

  public Product(String name,
                 String category,
                 String description,
                 String photoUrl,
                 BigDecimal price,
                 Integer stockQuantity) {
    this.name = name;
    this.category = category;
    this.description = description;
    this.photoUrl = photoUrl;
    this.price = price;
    this.stockQuantity = stockQuantity;
  }

  public Product(String name,
                 String category,
                 String description,
                 String photoUrl,
                 BigDecimal price,
                 Integer stockQuantity,
                 Double weightKg,
                 Double volumeM3) {
    this.name = name;
    this.category = category;
    this.description = description;
    this.photoUrl = photoUrl;
    this.price = price;
    this.stockQuantity = stockQuantity;
    this.weightKg = weightKg;
    this.volumeM3 = volumeM3;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getPhotoUrl() {
    return photoUrl;
  }

  public void setPhotoUrl(String photoUrl) {
    this.photoUrl = photoUrl;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public Integer getStockQuantity() {
    return stockQuantity;
  }

  public void setStockQuantity(Integer stockQuantity) {
    this.stockQuantity = stockQuantity;
  }

  public Double getWeightKg() {
    return weightKg;
  }

  public void setWeightKg(Double weightKg) {
    this.weightKg = weightKg;
  }

  public Double getVolumeM3() {
    return volumeM3;
  }

  public void setVolumeM3(Double volumeM3) {
    this.volumeM3 = volumeM3;
  }
}
