package com.farm.sales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.dto.ProfileResponse;
import com.farm.sales.dto.ProfileUpdateRequest;
import com.farm.sales.dto.StoreAddressRequest;
import com.farm.sales.dto.StoreAddressResponse;
import com.farm.sales.security.JwtClaimsReader;
import com.farm.sales.service.DirectorProfileService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

class DirectorProfileControllerTest {
  private DirectorProfileService directorProfileService;
  private JwtClaimsReader jwtClaimsReader;
  private DirectorProfileController controller;

  @BeforeEach
  void setUp() {
    directorProfileService = mock(DirectorProfileService.class);
    jwtClaimsReader = mock(JwtClaimsReader.class);
    controller = new DirectorProfileController(directorProfileService, jwtClaimsReader);
  }

  @Test
  void profileReturnsProfile() {
    Jwt jwt = mock(Jwt.class);
    when(jwtClaimsReader.requireUserId(jwt)).thenReturn(42L);
    ProfileResponse response = new ProfileResponse(
        42L,
        "director01",
        "Директор магазина 01",
        "+375291000001",
        "Магазин \"Демо 01\""
    );
    when(directorProfileService.getProfile(42L)).thenReturn(response);

    var httpResponse = controller.profile(jwt);

    assertThat(httpResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(httpResponse.getBody()).isEqualTo(response);
    verify(directorProfileService).getProfile(42L);
  }

  @Test
  void updateProfileReturnsUpdatedProfile() {
    Jwt jwt = mock(Jwt.class);
    when(jwtClaimsReader.requireUserId(jwt)).thenReturn(7L);
    ProfileUpdateRequest request = new ProfileUpdateRequest("Иван Иванов", "+375291234000");
    ProfileResponse response = new ProfileResponse(
        7L,
        "director",
        "Иван Иванов",
        "+375291234000",
        "ООО \"Ферма\""
    );
    when(directorProfileService.updateProfile(7L, request)).thenReturn(response);

    var httpResponse = controller.updateProfile(jwt, request);

    assertThat(httpResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(httpResponse.getBody()).isEqualTo(response);
    verify(directorProfileService).updateProfile(7L, request);
  }

  @Test
  void addressesReturnsList() {
    Jwt jwt = mock(Jwt.class);
    when(jwtClaimsReader.requireUserId(jwt)).thenReturn(11L);
    List<StoreAddressResponse> response = List.of(
        new StoreAddressResponse(
            1L,
            "Основной склад",
            "Могилёв, ул. Челюскинцев 105",
            new BigDecimal("53.8654"),
            new BigDecimal("30.2905"),
            true
        )
    );
    when(directorProfileService.getAddresses(11L)).thenReturn(response);

    var httpResponse = controller.addresses(jwt);

    assertThat(httpResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(httpResponse.getBody()).isEqualTo(response);
    verify(directorProfileService).getAddresses(11L);
  }

  @Test
  void createAddressReturnsCreated() {
    Jwt jwt = mock(Jwt.class);
    when(jwtClaimsReader.requireUserId(jwt)).thenReturn(15L);
    StoreAddressRequest request = new StoreAddressRequest(
        "Центральный магазин",
        "Могилёв, ул. Академика Павлова 3",
        new BigDecimal("53.9342"),
        new BigDecimal("30.2941")
    );
    StoreAddressResponse response = new StoreAddressResponse(
        3L,
        "Центральный магазин",
        "Могилёв, ул. Академика Павлова 3",
        new BigDecimal("53.9342"),
        new BigDecimal("30.2941"),
        true
    );
    when(directorProfileService.createAddress(15L, request)).thenReturn(response);

    var httpResponse = controller.createAddress(jwt, request);

    assertThat(httpResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(httpResponse.getBody()).isEqualTo(response);
    verify(directorProfileService).createAddress(15L, request);
  }

  @Test
  void updateAddressReturnsUpdated() {
    Jwt jwt = mock(Jwt.class);
    when(jwtClaimsReader.requireUserId(jwt)).thenReturn(15L);
    StoreAddressRequest request = new StoreAddressRequest(
        "Точка отгрузки",
        "Могилёв, пр-т Мира 42",
        new BigDecimal("53.8948"),
        new BigDecimal("30.3312")
    );
    StoreAddressResponse response = new StoreAddressResponse(
        6L,
        "Точка отгрузки",
        "Могилёв, пр-т Мира 42",
        new BigDecimal("53.8948"),
        new BigDecimal("30.3312"),
        true
    );
    when(directorProfileService.updateAddress(15L, 6L, request)).thenReturn(response);

    var httpResponse = controller.updateAddress(jwt, 6L, request);

    assertThat(httpResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(httpResponse.getBody()).isEqualTo(response);
    verify(directorProfileService).updateAddress(15L, 6L, request);
  }

  @Test
  void deleteAddressReturnsNoContent() {
    Jwt jwt = mock(Jwt.class);
    when(jwtClaimsReader.requireUserId(jwt)).thenReturn(15L);

    var httpResponse = controller.deleteAddress(jwt, 9L);

    assertThat(httpResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(directorProfileService).deleteAddress(15L, 9L);
  }
}
