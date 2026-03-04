package com.farm.sales.service;

import com.farm.sales.dto.GeoLookupResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class GeocodingService {
  private static final int RETRY_ATTEMPTS = 3;
  private static final long RETRY_BASE_DELAY_MS = 180L;
  private static final long CACHE_TTL_MS = 10 * 60 * 1000;
  private static final int CACHE_MAX_ENTRIES = 500;
  private final RestClient restClient;
  private final Map<String, CacheEntry<List<GeoLookupResponse>>> searchCache = new ConcurrentHashMap<>();
  private final Map<String, CacheEntry<GeoLookupResponse>> reverseCache = new ConcurrentHashMap<>();

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
    String cacheKey = normalized.toLowerCase(Locale.ROOT) + "|" + safeLimit;
    List<GeoLookupResponse> cached = readCache(searchCache, cacheKey);
    if (cached != null) {
      return cached;
    }

    JsonNode payload = requestWithRetry(
        () -> restClient.get()
          .uri(uriBuilder -> uriBuilder
              .path("/search")
              .queryParam("q", normalized)
              .queryParam("format", "jsonv2")
              .queryParam("limit", safeLimit)
              .build())
          .retrieve()
          .body(JsonNode.class),
        "Не удалось определить координаты адреса"
    );

    if (payload == null || !payload.isArray()) {
      writeCache(searchCache, cacheKey, List.of());
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
    List<GeoLookupResponse> immutableResults = List.copyOf(results);
    writeCache(searchCache, cacheKey, immutableResults);
    return immutableResults;
  }

  public GeoLookupResponse reverse(BigDecimal latitude, BigDecimal longitude) {
    String cacheKey = buildReverseCacheKey(latitude, longitude);
    GeoLookupResponse cached = readCache(reverseCache, cacheKey);
    if (cached != null) {
      return cached;
    }

    JsonNode payload = requestWithRetry(
        () -> restClient.get()
          .uri(uriBuilder -> uriBuilder
              .path("/reverse")
              .queryParam("lat", latitude)
              .queryParam("lon", longitude)
              .queryParam("format", "jsonv2")
              .build())
          .retrieve()
          .body(JsonNode.class),
        "Не удалось определить адрес по координатам"
    );

    if (payload == null || payload.isMissingNode()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Адрес по координатам не найден");
    }

    String displayName = payload.path("display_name").asText("").trim();
    if (displayName.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Адрес по координатам не найден");
    }

    BigDecimal resolvedLatitude = parseCoordinate(payload.path("lat").asText(null), 7);
    BigDecimal resolvedLongitude = parseCoordinate(payload.path("lon").asText(null), 7);

    GeoLookupResponse resolved = new GeoLookupResponse(
        payload.path("place_id").asText(""),
        displayName,
        resolvedLatitude != null ? resolvedLatitude : latitude,
        resolvedLongitude != null ? resolvedLongitude : longitude
    );
    writeCache(reverseCache, cacheKey, resolved);
    return resolved;
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

  private String buildReverseCacheKey(BigDecimal latitude, BigDecimal longitude) {
    return latitude.setScale(5, RoundingMode.HALF_UP).toPlainString()
        + "|"
        + longitude.setScale(5, RoundingMode.HALF_UP).toPlainString();
  }

  private <T> T requestWithRetry(Supplier<T> supplier, String errorMessage) {
    RestClientException lastError = null;
    for (int attempt = 1; attempt <= RETRY_ATTEMPTS; attempt++) {
      try {
        return supplier.get();
      } catch (RestClientException ex) {
        lastError = ex;
        if (attempt >= RETRY_ATTEMPTS) {
          break;
        }
        sleepBackoff(attempt);
      }
    }
    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, errorMessage, lastError);
  }

  private void sleepBackoff(int attempt) {
    long delayMs = RETRY_BASE_DELAY_MS * attempt;
    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
    }
  }

  private <T> T readCache(Map<String, CacheEntry<T>> cache, String key) {
    CacheEntry<T> entry = cache.get(key);
    if (entry == null) {
      return null;
    }
    if (entry.expiresAt() <= System.currentTimeMillis()) {
      cache.remove(key, entry);
      return null;
    }
    return entry.value();
  }

  private <T> void writeCache(Map<String, CacheEntry<T>> cache, String key, T value) {
    pruneExpiredEntries(cache);
    cache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + CACHE_TTL_MS));

    while (cache.size() > CACHE_MAX_ENTRIES) {
      String keyToEvict = cache.keySet().stream().findFirst().orElse(null);
      if (keyToEvict == null) {
        return;
      }
      cache.remove(keyToEvict);
    }
  }

  private <T> void pruneExpiredEntries(Map<String, CacheEntry<T>> cache) {
    long now = System.currentTimeMillis();
    cache.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
  }

  private record CacheEntry<T>(T value, long expiresAt) {
  }
}
