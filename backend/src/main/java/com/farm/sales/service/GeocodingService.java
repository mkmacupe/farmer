package com.farm.sales.service;

import com.farm.sales.dto.GeoLookupResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

@Service
public class GeocodingService {
  private final RestClient restClient;

  public GeocodingService(@Value("${app.geo.nominatim-base-url:https://nominatim.openstreetmap.org}")
                          String nominatimBaseUrl,
                          @Value("${app.geo.user-agent:FarmSalesCourseProject/1.0}")
                          String userAgent,
                          @Value("${app.geo.connect-timeout-ms:3000}")
                          int connectTimeoutMs,
                          @Value("${app.geo.read-timeout-ms:5000}")
                          int readTimeoutMs) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Math.max(connectTimeoutMs, 100));
    requestFactory.setReadTimeout(Math.max(readTimeoutMs, 100));

    this.restClient = RestClient.builder()
        .baseUrl(nominatimBaseUrl)
        .requestFactory(requestFactory)
        .defaultHeader("User-Agent", userAgent)
        .build();
  }

  public List<GeoLookupResponse> search(String query, int limit) {
    String normalized = query == null ? "" : query.trim();
    if (normalized.isEmpty()) {
      return List.of();
    }

    int safeLimit = Math.max(1, Math.min(limit, 10));
    JsonNode payload;
    try {
      payload = restClient.get()
          .uri(uriBuilder -> uriBuilder
              .path("/search")
              .queryParam("q", normalized)
              .queryParam("format", "jsonv2")
              .queryParam("limit", safeLimit)
              .build())
          .retrieve()
          .body(JsonNode.class);
    } catch (RestClientException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось определить координаты адреса");
    }

    if (payload == null || !payload.isArray()) {
      return List.of();
    }

    List<GeoLookupResponse> results = new ArrayList<>();
    for (JsonNode node : payload) {
      BigDecimal latitude = parseCoordinate(node.path("lat").asText(null), 7);
      BigDecimal longitude = parseCoordinate(node.path("lon").asText(null), 7);
      results.add(new GeoLookupResponse(
          node.path("place_id").asText(""),
          node.path("display_name").asText(""),
          latitude,
          longitude
      ));
    }
    return results;
  }

  public GeoLookupResponse reverse(BigDecimal latitude, BigDecimal longitude) {
    JsonNode payload;
    try {
      payload = restClient.get()
          .uri(uriBuilder -> uriBuilder
              .path("/reverse")
              .queryParam("lat", latitude)
              .queryParam("lon", longitude)
              .queryParam("format", "jsonv2")
              .build())
          .retrieve()
          .body(JsonNode.class);
    } catch (RestClientException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось определить адрес по координатам");
    }

    if (payload == null || payload.isMissingNode()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Адрес по координатам не найден");
    }

    String displayName = payload.path("display_name").asText("").trim();
    if (displayName.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Адрес по координатам не найден");
    }

    BigDecimal resolvedLatitude = parseCoordinate(payload.path("lat").asText(null), 7);
    BigDecimal resolvedLongitude = parseCoordinate(payload.path("lon").asText(null), 7);

    return new GeoLookupResponse(
        payload.path("place_id").asText(""),
        displayName,
        resolvedLatitude != null ? resolvedLatitude : latitude,
        resolvedLongitude != null ? resolvedLongitude : longitude
    );
  }

  public Optional<GeoLookupResponse> geocodeFirst(String addressLine) {
    List<GeoLookupResponse> candidates = search(addressLine, 1);
    if (candidates.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(candidates.get(0));
  }

  private BigDecimal parseCoordinate(String value, int scale) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(value).setScale(scale, RoundingMode.HALF_UP);
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
