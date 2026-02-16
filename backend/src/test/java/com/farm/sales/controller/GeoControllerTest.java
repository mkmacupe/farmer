package com.farm.sales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.dto.GeoLookupResponse;
import com.farm.sales.service.GeocodingService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GeoControllerTest {
  private GeocodingService geocodingService;
  private GeoController controller;

  @BeforeEach
  void setUp() {
    geocodingService = mock(GeocodingService.class);
    controller = new GeoController(geocodingService);
  }

  @Test
  void lookupReturnsResults() {
    List<GeoLookupResponse> response = List.of(
        new GeoLookupResponse(
            "place-1",
            "Минск, Беларусь",
            new BigDecimal("53.9023"),
            new BigDecimal("27.5618")
        )
    );
    when(geocodingService.search("Минск", 5)).thenReturn(response);

    var httpResponse = controller.lookup("Минск", 5);

    assertThat(httpResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(httpResponse.getBody()).isEqualTo(response);
    verify(geocodingService).search("Минск", 5);
  }

  @Test
  void reverseReturnsResolvedAddress() {
    GeoLookupResponse response = new GeoLookupResponse(
        "place-2",
        "Беларусь, Могилёв, Ленинская улица, 10",
        new BigDecimal("53.9006000"),
        new BigDecimal("30.3317000")
    );
    when(geocodingService.reverse(new BigDecimal("53.9006"), new BigDecimal("30.3317")))
        .thenReturn(response);

    var httpResponse = controller.reverse(new BigDecimal("53.9006"), new BigDecimal("30.3317"));

    assertThat(httpResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(httpResponse.getBody()).isEqualTo(response);
    verify(geocodingService).reverse(new BigDecimal("53.9006"), new BigDecimal("30.3317"));
  }
}
