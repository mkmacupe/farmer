package com.farm.sales.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, columnDefinition = "text")
  private String username;

  @Column(name = "password_hash", nullable = false, columnDefinition = "text")
  private String passwordHash;

  @Column(name = "full_name", nullable = false, columnDefinition = "text")
  private String fullName;

  @Column(name = "phone", columnDefinition = "text")
  private String phone;

  @Column(name = "legal_entity_name", columnDefinition = "text")
  private String legalEntityName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  public User() {
  }

  public User(String username,
              String passwordHash,
              String fullName,
              String phone,
              String legalEntityName,
              Role role) {
    this.username = username;
    this.passwordHash = passwordHash;
    this.fullName = fullName;
    this.phone = phone;
    this.legalEntityName = legalEntityName;
    this.role = role;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getLegalEntityName() {
    return legalEntityName;
  }

  public void setLegalEntityName(String legalEntityName) {
    this.legalEntityName = legalEntityName;
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
  }
}
