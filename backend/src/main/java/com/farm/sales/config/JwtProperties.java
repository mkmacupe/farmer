package com.farm.sales.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
  private String secret;
  private String issuer;
  private long expirationMinutes;

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  public long getExpirationMinutes() {
    return expirationMinutes;
  }

  public void setExpirationMinutes(long expirationMinutes) {
    this.expirationMinutes = expirationMinutes;
  }
}
