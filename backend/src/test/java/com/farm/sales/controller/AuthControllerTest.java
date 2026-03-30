package com.farm.sales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.dto.AuthRequest;
import com.farm.sales.dto.AuthResponse;
import com.farm.sales.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AuthControllerTest {
  private AuthService authService;
  private AuthController controller;

  @BeforeEach
  void setUp() {
    authService = mock(AuthService.class);
    controller = new AuthController(authService);
  }

  @Test
  void loginDelegatesToServiceAndReturnsOk() {
    AuthRequest request = new AuthRequest("manager", "secret123");
    AuthResponse response = new AuthResponse("token", "manager", "Manager", "MANAGER");
    when(authService.login(request)).thenReturn(response);

    var httpResponse = controller.login(request);

    assertThat(httpResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(httpResponse.getBody()).isEqualTo(response);
    verify(authService).login(request);
  }

}
