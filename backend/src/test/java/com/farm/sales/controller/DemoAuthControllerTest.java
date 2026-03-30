package com.farm.sales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.dto.AuthResponse;
import com.farm.sales.dto.DemoLoginRequest;
import com.farm.sales.service.AuthService;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;

class DemoAuthControllerTest {
  private AuthService authService;
  private DemoAuthController controller;

  @BeforeEach
  void setUp() {
    authService = mock(AuthService.class);
    controller = new DemoAuthController(authService);
  }

  @Test
  void demoLoginDelegatesToServiceAndReturnsOk() {
    DemoLoginRequest request = new DemoLoginRequest("manager", "secret123");
    AuthResponse response = new AuthResponse("token", "manager", "Manager", "MANAGER");
    when(authService.demoLogin("manager", "secret123")).thenReturn(response);

    var httpResponse = controller.demoLogin(request);

    assertThat(httpResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(httpResponse.getBody()).isEqualTo(response);
    verify(authService).demoLogin("manager", "secret123");
  }

  @Test
  void seedLoginRouteUsesSeedLoginPath() throws NoSuchMethodException {
    PostMapping mapping = DemoAuthController.class
        .getDeclaredMethod("demoLogin", DemoLoginRequest.class)
        .getAnnotation(PostMapping.class);

    assertThat(mapping).isNotNull();
    assertThat(Arrays.asList(mapping.value())).containsExactly("/seed-login");
  }
}
