package com.farm.sales.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class SecurityAuthorizationIntegrationTest {
  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply(springSecurity())
        .build();
  }

  @Test
  void productsRequireAuthentication() throws Exception {
    mockMvc.perform(get("/api/products"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void directorCannotCreateProduct() throws Exception {
    mockMvc.perform(post("/api/products")
            .with(jwtFor("director", 10L, "DIRECTOR"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Яблоки",
                  "category": "Фрукты",
                  "description": "Зелёные яблоки",
                  "photoUrl": "https://example.com/apples.jpg",
                  "price": 12.50,
                  "stockQuantity": 10
                }
                """))
        .andExpect(status().isForbidden());
  }

  @Test
  void managerCanCreateProduct() throws Exception {
    mockMvc.perform(post("/api/products")
            .with(jwtFor("manager", 1L, "MANAGER"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Яблоки",
                  "category": "Фрукты",
                  "description": "Зелёные яблоки",
                  "photoUrl": "https://example.com/apples.jpg",
                  "price": 12.50,
                  "stockQuantity": 10
                }
                """))
        .andExpect(status().isCreated());
  }

  @Test
  void driverCannotReadAuditLogs() throws Exception {
    mockMvc.perform(get("/api/audit/logs")
            .with(jwtFor("driver", 2L, "DRIVER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void managerCanReadAuditLogs() throws Exception {
    mockMvc.perform(get("/api/audit/logs")
            .with(jwtFor("manager", 1L, "MANAGER")))
        .andExpect(status().isOk());
  }

  @Test
  void actuatorHealthIsPublic() throws Exception {
    mockMvc.perform(get("/actuator/health"))
        .andExpect(status().isOk());
  }

  @Test
  void openApiRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/v3/api-docs"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void openApiIsAvailableForAuthenticatedUsers() throws Exception {
    mockMvc.perform(get("/v3/api-docs")
            .with(jwtFor("manager", 1L, "MANAGER")))
        .andExpect(status().isOk());
  }

  @Test
  void notificationStreamRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/notifications/stream"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void buyerRegistrationIsNotPublic() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "username": "buyer_public",
                  "password": "1",
                  "fullName": "Public Buyer",
                  "phone": "+375291112233",
                  "legalEntityName": ""
                }
                """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void managerCanReadDashboard() throws Exception {
    mockMvc.perform(get("/api/dashboard/summary")
            .with(jwtFor("manager", 1L, "MANAGER")))
        .andExpect(status().isOk());
  }

  @Test
  void directorCannotReadDashboard() throws Exception {
    mockMvc.perform(get("/api/dashboard/summary")
            .with(jwtFor("director", 10L, "DIRECTOR")))
        .andExpect(status().isForbidden());
  }

  private RequestPostProcessor jwtFor(String subject, long userId, String role) {
    return jwt()
        .authorities(new SimpleGrantedAuthority("ROLE_" + role))
        .jwt(token -> token
            .subject(subject)
            .claim("userId", userId)
            .claim("roles", List.of(role)));
  }
}
