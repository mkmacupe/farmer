package com.farm.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.farm.sales.dto.GeoLookupResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class GeocodingServiceTest {
  private HttpServer server;
  private AtomicReference<String> searchBody;
  private AtomicReference<String> reverseBody;
  private AtomicInteger searchStatus;
  private AtomicInteger reverseStatus;
  private AtomicReference<String> lastSearchQuery;
  private AtomicReference<String> lastReverseQuery;
  private GeocodingService service;

  @BeforeEach
  void setUp() throws IOException {
    searchBody = new AtomicReference<>("[]");
    reverseBody = new AtomicReference<>("{}");
    searchStatus = new AtomicInteger(200);
    reverseStatus = new AtomicInteger(200);
    lastSearchQuery = new AtomicReference<>("");
    lastReverseQuery = new AtomicReference<>("");

    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/search", exchange -> respond(exchange, searchStatus.get(), searchBody.get(), lastSearchQuery));
    server.createContext("/reverse", exchange -> respond(exchange, reverseStatus.get(), reverseBody.get(), lastReverseQuery));
    server.start();
    service = new GeocodingService("http://localhost:" + server.getAddress().getPort(), "test-agent", 3000, 5000);
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void searchNormalizesInputClampsLimitAndParsesCoordinates() {
    searchBody.set("""
        [
          {"place_id":"a1","display_name":"Минск","lat":"53.9023","lon":"27.5618"},
          {"place_id":"a2","display_name":"Invalid","lat":"bad","lon":""}
        ]
        """);

    List<GeoLookupResponse> results = service.search("  Минск  ", 99);

    assertThat(results).hasSize(2);
    assertThat(results.get(0).placeId()).isEqualTo("a1");
    assertThat(results.get(0).displayName()).isEqualTo("Минск");
    assertThat(results.get(0).latitude()).isEqualByComparingTo("53.9023000");
    assertThat(results.get(0).longitude()).isEqualByComparingTo("27.5618000");
    assertThat(results.get(1).latitude()).isNull();
    assertThat(results.get(1).longitude()).isNull();
    assertThat(lastSearchQuery.get()).contains("q=");
    assertThat(lastSearchQuery.get()).contains("format=jsonv2");
    assertThat(lastSearchQuery.get()).contains("limit=10");

    service.search("abc", -5);
    assertThat(lastSearchQuery.get()).contains("limit=1");
    assertThat(service.search("   ", 5)).isEmpty();
    assertThat(service.search(null, 5)).isEmpty();
  }

  @Test
  void searchHandlesNonArrayPayloadAndGatewayErrors() {
    searchBody.set("{\"unexpected\":true}");
    assertThat(service.search("addr", 3)).isEmpty();

    searchStatus.set(204);
    searchBody.set("");
    assertThat(service.search("addr", 3)).isEmpty();

    searchStatus.set(200);
    searchBody.set(null);
    assertThat(service.search("addr", 3)).isEmpty();

    GeocodingService failing = new GeocodingService("http://127.0.0.1:1", "ua", 3000, 5000);
    assertStatus(
        () -> failing.search("addr", 1),
        HttpStatus.BAD_GATEWAY,
        "Не удалось определить координаты адреса"
    );
  }

  @Test
  void reverseReturnsResponseAndFallsBackToRequestCoordinatesWhenNeeded() {
    reverseBody.set("""
        {
          "place_id":"p1",
          "display_name":"  Могилёв, Беларусь  ",
          "lat":"bad",
          "lon":""
        }
        """);

    GeoLookupResponse response = service.reverse(new BigDecimal("53.9006000"), new BigDecimal("30.3317000"));

    assertThat(response.placeId()).isEqualTo("p1");
    assertThat(response.displayName()).isEqualTo("Могилёв, Беларусь");
    assertThat(response.latitude()).isEqualByComparingTo("53.9006000");
    assertThat(response.longitude()).isEqualByComparingTo("30.3317000");
    assertThat(lastReverseQuery.get()).contains("lat=53.9006000");
    assertThat(lastReverseQuery.get()).contains("lon=30.3317000");
  }

  @Test
  void reverseUsesResolvedCoordinatesWhenPresent() {
    reverseBody.set("""
        {
          "place_id":"p2",
          "display_name":"Минск",
          "lat":"53.1111111",
          "lon":"27.2222222"
        }
        """);

    GeoLookupResponse response = service.reverse(new BigDecimal("1.0000000"), new BigDecimal("2.0000000"));

    assertThat(response.latitude()).isEqualByComparingTo("53.1111111");
    assertThat(response.longitude()).isEqualByComparingTo("27.2222222");
  }

  @Test
  void reverseHandlesNotFoundAndGatewayErrors() {
    reverseStatus.set(204);
    reverseBody.set("");
    assertStatus(
        () -> service.reverse(new BigDecimal("53.1"), new BigDecimal("30.1")),
        HttpStatus.NOT_FOUND,
        "не найден"
    );

    reverseStatus.set(200);
    reverseBody.set("{\"display_name\":\"   \"}");
    assertStatus(
        () -> service.reverse(new BigDecimal("53.1"), new BigDecimal("30.1")),
        HttpStatus.NOT_FOUND,
        "не найден"
    );

    reverseBody.set(null);
    assertStatus(
        () -> service.reverse(new BigDecimal("53.1"), new BigDecimal("30.1")),
        HttpStatus.NOT_FOUND,
        "не найден"
    );

    GeocodingService failing = new GeocodingService("http://127.0.0.1:1", "ua", 3000, 5000);
    assertStatus(
        () -> failing.reverse(new BigDecimal("53.1"), new BigDecimal("30.1")),
        HttpStatus.BAD_GATEWAY,
        "Не удалось определить адрес"
    );
  }

  @Test
  void geocodeFirstReturnsOptionalFirstElementOrEmpty() {
    GeocodingService spy = spy(new GeocodingService("http://localhost:" + server.getAddress().getPort(), "ua", 3000, 5000));
    GeoLookupResponse first = new GeoLookupResponse(
        "id-1",
        "Address",
        new BigDecimal("53.1234567"),
        new BigDecimal("30.7654321")
    );
    doReturn(List.of(first)).when(spy).search("Address", 1);
    assertThat(spy.geocodeFirst("Address")).contains(first);

    doReturn(List.of()).when(spy).search("Missing", 1);
    assertThat(spy.geocodeFirst("Missing")).isEqualTo(Optional.empty());
  }

  @Test
  void parseCoordinateHandlesNullBlankAndRounding() throws Exception {
    Method method = GeocodingService.class.getDeclaredMethod("parseCoordinate", String.class, int.class);
    method.setAccessible(true);

    assertThat(method.invoke(service, null, 7)).isNull();
    assertThat(method.invoke(service, "   ", 7)).isNull();
    assertThat((BigDecimal) method.invoke(service, "53.123456789", 7))
        .isEqualByComparingTo("53.1234568");
  }

  @Test
  void reverseHandlesMissingNodePayload() throws Exception {
    GeocodingService local = new GeocodingService("http://localhost:" + server.getAddress().getPort(), "ua", 3000, 5000);

    RestClient restClient = mock(RestClient.class);
    @SuppressWarnings("rawtypes")
    RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
    @SuppressWarnings("rawtypes")
    RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.get()).thenReturn(uriSpec);
    @SuppressWarnings("unchecked")
    java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uriFunction =
        (java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>) any(java.util.function.Function.class);
    when(uriSpec.uri(uriFunction)).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(JsonNode.class)).thenReturn(MissingNode.getInstance());

    Field field = GeocodingService.class.getDeclaredField("restClient");
    field.setAccessible(true);
    field.set(local, restClient);

    assertStatus(
        () -> local.reverse(new BigDecimal("53.1"), new BigDecimal("30.1")),
        HttpStatus.NOT_FOUND,
        "не найден"
    );
  }

  private void respond(HttpExchange exchange,
                       int statusCode,
                       String body,
                       AtomicReference<String> queryHolder) throws IOException {
    queryHolder.set(exchange.getRequestURI().getQuery());
    byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(statusCode, payload.length);
    exchange.getResponseBody().write(payload);
    exchange.close();
  }

  private void assertStatus(Runnable runnable, HttpStatus status, String reasonPart) {
    assertThatThrownBy(runnable::run)
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(status);
          assertThat(ex.getReason()).contains(reasonPart);
        });
  }
}
