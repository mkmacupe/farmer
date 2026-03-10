package com.farm.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

class RoadRoutingServiceTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private RestClient restClient;
  @SuppressWarnings("rawtypes")
  private RestClient.RequestHeadersUriSpec uriSpec;
  @SuppressWarnings("rawtypes")
  private RestClient.RequestHeadersSpec headersSpec;
  private RestClient.ResponseSpec responseSpec;
  private RoadRoutingService service;

  @BeforeEach
  void setUp() {
    restClient = mock(RestClient.class);
    uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
    headersSpec = mock(RestClient.RequestHeadersSpec.class);
    responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.get()).thenReturn(uriSpec);
    when(uriSpec.uri(any(java.util.function.Function.class))).thenReturn(headersSpec);
    when(uriSpec.uri(org.mockito.ArgumentMatchers.anyString())).thenReturn(headersSpec);
    when(uriSpec.uri(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.<Object[]>any()))
        .thenReturn(headersSpec);
    when(uriSpec.uri(any(java.net.URI.class))).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);

    service = new RoadRoutingService(restClient, 60_000, 100, 50);
  }

  @Test
  void drivingDistancesKmParsesMetersIntoKilometers() throws Exception {
    JsonNode payload = objectMapper.readTree("""
        {"code":"Ok","distances":[[1500.0,2750.0]]}
        """);
    when(responseSpec.body(JsonNode.class)).thenReturn(payload);

    var source = new RoadRoutingService.RouteCoordinate(53.8971270, 30.3320410);
    var destinations = List.of(
        new RoadRoutingService.RouteCoordinate(53.9400000, 30.3400000),
        new RoadRoutingService.RouteCoordinate(53.8700000, 30.4100000)
    );

    List<Double> distances = service.drivingDistancesKm(source, destinations);

    assertThat(distances).containsExactly(1.5, 2.75);
  }

  @Test
  void drivingDistanceMatrixKmParsesRowsAndSeedsPairCache() throws Exception {
    JsonNode payload = objectMapper.readTree("""
        {"code":"Ok","distances":[[1500.0,2750.0],[900.0,1100.0]]}
        """);
    when(responseSpec.body(JsonNode.class)).thenReturn(payload);

    var sources = List.of(
        new RoadRoutingService.RouteCoordinate(53.8971270, 30.3320410),
        new RoadRoutingService.RouteCoordinate(53.9050000, 30.3500000)
    );
    var destinations = List.of(
        new RoadRoutingService.RouteCoordinate(53.9400000, 30.3400000),
        new RoadRoutingService.RouteCoordinate(53.8700000, 30.4100000)
    );

    List<List<Double>> matrix = service.drivingDistanceMatrixKm(sources, destinations);
    double cachedDistance = service.drivingDistanceKm(sources.get(1), destinations.get(0));

    assertThat(matrix).containsExactly(
        List.of(1.5, 2.75),
        List.of(0.9, 1.1)
    );
    assertThat(cachedDistance).isEqualTo(0.9);
    verify(responseSpec, times(1)).body(JsonNode.class);
  }

  @Test
  void drivingRouteGeometryReturnsFullPolylineWithExactEndpoints() throws Exception {
    JsonNode payload = objectMapper.readTree("""
        {
          "code":"Ok",
          "routes":[
            {
              "geometry":{
                "coordinates":[
                  [30.3321000,53.8972000],
                  [30.3365000,53.9180000],
                  [30.3401000,53.9401000]
                ]
              }
            }
          ]
        }
        """);
    when(responseSpec.body(JsonNode.class)).thenReturn(payload);

    var source = new RoadRoutingService.RouteCoordinate(53.8971270, 30.3320410);
    var destination = new RoadRoutingService.RouteCoordinate(53.9400000, 30.3400000);

    List<RoadRoutingService.RouteCoordinate> geometry = service.drivingRouteGeometry(List.of(source, destination));

    assertThat(geometry).isNotEmpty();
    assertThat(geometry.getFirst()).isEqualTo(source);
    assertThat(geometry.getLast()).isEqualTo(destination);
  }

  @Test
  void drivingRouteGeometryUsesCacheForRepeatedWaypoints() throws Exception {
    JsonNode payload = objectMapper.readTree("""
        {
          "code":"Ok",
          "routes":[
            {
              "geometry":{
                "coordinates":[
                  [30.3321000,53.8972000],
                  [30.3365000,53.9180000],
                  [30.3401000,53.9401000]
                ]
              }
            }
          ]
        }
        """);
    when(responseSpec.body(JsonNode.class)).thenReturn(payload);

    var source = new RoadRoutingService.RouteCoordinate(53.8971270, 30.3320410);
    var destination = new RoadRoutingService.RouteCoordinate(53.9400000, 30.3400000);

    List<RoadRoutingService.RouteCoordinate> first = service.drivingRouteGeometry(List.of(source, destination));
    List<RoadRoutingService.RouteCoordinate> second = service.drivingRouteGeometry(List.of(source, destination));

    assertThat(first).isEqualTo(second);
    verify(responseSpec, times(1)).body(JsonNode.class);
  }

  @Test
  void drivingDistanceKmUsesCacheForRepeatedPair() throws Exception {
    JsonNode payload = objectMapper.readTree("""
        {"code":"Ok","distances":[[1500.0]]}
        """);
    when(responseSpec.body(JsonNode.class)).thenReturn(payload);

    var source = new RoadRoutingService.RouteCoordinate(53.8971270, 30.3320410);
    var destination = new RoadRoutingService.RouteCoordinate(53.9400000, 30.3400000);

    double first = service.drivingDistanceKm(source, destination);
    double second = service.drivingDistanceKm(source, destination);

    assertThat(first).isEqualTo(1.5);
    assertThat(second).isEqualTo(1.5);
    org.mockito.Mockito.verify(responseSpec, org.mockito.Mockito.times(1)).body(JsonNode.class);
  }

  @Test
  void drivingDistancesKmRejectsInvalidServiceResponses() throws Exception {
    var source = new RoadRoutingService.RouteCoordinate(53.8971270, 30.3320410);
    var destinations = List.of(new RoadRoutingService.RouteCoordinate(53.9400000, 30.3400000));

    when(responseSpec.body(JsonNode.class)).thenReturn(objectMapper.readTree("""
        {"code":"NoRoute","distances":[]}
        """));
    assertStatus(() -> service.drivingDistancesKm(source, destinations), HttpStatus.BAD_GATEWAY);

    when(responseSpec.body(JsonNode.class)).thenReturn(objectMapper.readTree("""
        {"code":"Ok","distances":[[null]]}
        """));
    assertStatus(() -> service.drivingDistancesKm(source, destinations), HttpStatus.BAD_GATEWAY);
  }

  @Test
  void drivingDistancesKmEntersCooldownAfterFirstRemoteFailure() {
    var source = new RoadRoutingService.RouteCoordinate(53.8971270, 30.3320410);
    var destinations = List.of(new RoadRoutingService.RouteCoordinate(53.9400000, 30.3400000));

    when(responseSpec.body(JsonNode.class)).thenThrow(new RestClientException("osrm-down"));

    assertStatus(() -> service.drivingDistancesKm(source, destinations), HttpStatus.BAD_GATEWAY);
    assertStatus(() -> service.drivingDistancesKm(source, destinations), HttpStatus.BAD_GATEWAY);

    verify(responseSpec, times(1)).body(JsonNode.class);
  }

  @Test
  void drivingRouteGeometryIsNotBlockedByDistanceCooldown() throws Exception {
    var source = new RoadRoutingService.RouteCoordinate(53.8971270, 30.3320410);
    var destination = new RoadRoutingService.RouteCoordinate(53.9400000, 30.3400000);

    JsonNode geometryPayload = objectMapper.readTree("""
        {
          "code":"Ok",
          "routes":[
            {
              "geometry":{
                "coordinates":[
                  [30.3321000,53.8972000],
                  [30.3365000,53.9180000],
                  [30.3401000,53.9401000]
                ]
              }
            }
          ]
        }
        """);
    when(responseSpec.body(JsonNode.class))
        .thenThrow(new RestClientException("osrm-down"))
        .thenReturn(geometryPayload);

    assertStatus(() -> service.drivingDistancesKm(source, List.of(destination)), HttpStatus.BAD_GATEWAY);

    List<RoadRoutingService.RouteCoordinate> geometry = service.drivingRouteGeometry(List.of(source, destination));

    assertThat(geometry).isNotEmpty();
    assertThat(geometry.getFirst()).isEqualTo(source);
    assertThat(geometry.getLast()).isEqualTo(destination);
    verify(responseSpec, times(2)).body(JsonNode.class);
  }

  private void assertStatus(Runnable runnable, HttpStatus status) {
    assertThatThrownBy(runnable::run)
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(status));
  }
}
