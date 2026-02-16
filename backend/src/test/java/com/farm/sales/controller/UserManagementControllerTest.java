package com.farm.sales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.dto.CreateDirectorRequest;
import com.farm.sales.dto.UserSummaryResponse;
import com.farm.sales.service.UserManagementService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class UserManagementControllerTest {
  private UserManagementService userManagementService;
  private UserManagementController controller;

  @BeforeEach
  void setUp() {
    userManagementService = mock(UserManagementService.class);
    controller = new UserManagementController(userManagementService);
  }

  @Test
  void createDirectorReturnsCreated() {
    CreateDirectorRequest request = new CreateDirectorRequest(
        "director1",
        "secret",
        "Иван Петров",
        "+375291234567",
        "ООО \"Ферма\""
    );
    UserSummaryResponse response = new UserSummaryResponse(
        10L,
        "director1",
        "Иван Петров",
        "+375291234567",
        "ООО \"Ферма\"",
        "DIRECTOR"
    );
    when(userManagementService.createDirector(request)).thenReturn(response);

    var httpResponse = controller.createDirector(request);

    assertThat(httpResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(httpResponse.getBody()).isEqualTo(response);
    verify(userManagementService).createDirector(request);
  }

  @Test
  void directorsReturnsList() {
    List<UserSummaryResponse> response = List.of(
        new UserSummaryResponse(1L, "director", "Олег Курилин", "+375291234567", "ОАО \"Могилевхимволокно\"", "DIRECTOR")
    );
    when(userManagementService.listDirectors()).thenReturn(response);

    var httpResponse = controller.directors();

    assertThat(httpResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(httpResponse.getBody()).isEqualTo(response);
    verify(userManagementService).listDirectors();
  }

  @Test
  void driversReturnsList() {
    List<UserSummaryResponse> response = List.of(
        new UserSummaryResponse(5L, "driver1", "Водитель 1", "+375291111111", null, "DRIVER")
    );
    when(userManagementService.listDrivers()).thenReturn(response);

    var httpResponse = controller.drivers();

    assertThat(httpResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(httpResponse.getBody()).isEqualTo(response);
    verify(userManagementService).listDrivers();
  }
}
