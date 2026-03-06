package com.farm.sales.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class RoadRoutingService {
  private static final long DEFAULT_CACHE_TTL_MS = 15 * 60 * 1000L;
  private static final int DEFAULT_CACHE_MAX_ENTRIES = 10_000;
  private final RestClient restClient;
  private final long cacheTtlMs;
  private final int cacheMaxEntries;
  private final int maxTableCoordinates;
  private final Map<String, CacheEntry<Double>> distanceCache = new ConcurrentHashMap<>();

  public RoadRoutingService(
      @Value("${app.routing.osrm-base-url:https://router.project-osrm.org}") String baseUrl,
      @Value("${app.routing.user-agent:FarmSalesCourseProject/1.0}") String userAgent,
      @Value("${app.routing.connect-timeout-ms:3000}") int connectTimeoutMs,
      @Value("${app.routing.read-timeout-ms:6000}") int readTimeoutMs,
      @Value("${app.routing.cache-ttl-ms:" + DEFAULT_CACHE_TTL_MS + "}") long cacheTtlMs,
      @Value("${app.routing.cache-max-entries:" + DEFAULT_CACHE_MAX_ENTRIES + "}") int cacheMaxEntries,
      @Value("${app.routing.max-table-coordinates:50}") int maxTableCoordinates
  ) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Math.max(connectTimeoutMs, 100));
    requestFactory.setReadTimeout(Math.max(readTimeoutMs, 100));

    this.restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(requestFactory)
        .defaultHeader("User-Agent", userAgent)
        .build();
    this.cacheTtlMs = Math.max(1_000L, cacheTtlMs);
    this.cacheMaxEntries = Math.max(100, cacheMaxEntries);
    this.maxTableCoordinates = Math.max(2, maxTableCoordinates);
  }

  RoadRoutingService(RestClient restClient, long cacheTtlMs, int cacheMaxEntries, int maxTableCoordinates) {
    this.restClient = restClient;
    this.cacheTtlMs = Math.max(1_000L, cacheTtlMs);
    this.cacheMaxEntries = Math.max(100, cacheMaxEntries);
    this.maxTableCoordinates = Math.max(2, maxTableCoordinates);
  }

  public double drivingDistanceKm(RouteCoordinate from, RouteCoordinate to) {
    if (from.equals(to)) {
      return 0.0;
    }

    String cacheKey = cacheKey(from, to);
    Double cached = readCache(cacheKey);
    if (cached != null) {
      return cached;
    }

    List<Double> distances = drivingDistancesKm(from, List.of(to));
    if (distances.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Роутинг-сервис не вернул расстояние");
    }
    return distances.get(0);
  }

  public List<Double> drivingDistancesKm(RouteCoordinate source, List<RouteCoordinate> destinations) {
    if (destinations == null || destinations.isEmpty()) {
      return List.of();
    }

    List<Double> results = new ArrayList<>(destinations.size());
    List<RouteCoordinate> unknownDestinations = new ArrayList<>();
    List<Integer> unknownIndexes = new ArrayList<>();

    for (int index = 0; index < destinations.size(); index++) {
      RouteCoordinate destination = destinations.get(index);
      if (source.equals(destination)) {
        results.add(0.0);
        continue;
      }

      String key = cacheKey(source, destination);
      Double cached = readCache(key);
      if (cached != null) {
        results.add(cached);
        continue;
      }

      results.add(null);
      unknownDestinations.add(destination);
      unknownIndexes.add(index);
    }

    if (unknownDestinations.isEmpty()) {
      return List.copyOf(results);
    }

    int fromIndex = 0;
    while (fromIndex < unknownDestinations.size()) {
      int toIndex = Math.min(fromIndex + maxTableCoordinates - 1, unknownDestinations.size());
      List<RouteCoordinate> chunk = unknownDestinations.subList(fromIndex, toIndex);
      List<Double> chunkDistances = requestTableDistances(source, chunk);

      for (int offset = 0; offset < chunk.size(); offset++) {
        RouteCoordinate destination = chunk.get(offset);
        Double distanceKm = chunkDistances.get(offset);
        int resultIndex = unknownIndexes.get(fromIndex + offset);
        results.set(resultIndex, distanceKm);
        writeCache(cacheKey(source, destination), distanceKm);
      }
      fromIndex = toIndex;
    }

    return List.copyOf(results);
  }

  private List<Double> requestTableDistances(RouteCoordinate source, List<RouteCoordinate> destinations) {
    String coordinates = buildCoordinates(source, destinations);
    String destinationsParam = buildDestinationsParam(destinations.size());

    JsonNode payload;
    try {
      payload = restClient.get()
          .uri(uriBuilder -> uriBuilder
              .path("/table/v1/driving/" + coordinates)
              .queryParam("sources", "0")
              .queryParam("destinations", destinationsParam)
              .queryParam("annotations", "distance")
              .build())
          .retrieve()
          .body(JsonNode.class);
    } catch (RestClientException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось получить дорожные расстояния", exception);
    }

    if (payload == null || !"Ok".equals(payload.path("code").asText())) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Роутинг-сервис вернул некорректный ответ");
    }

    JsonNode distancesNode = payload.path("distances");
    if (!distancesNode.isArray() || distancesNode.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Роутинг-сервис не вернул матрицу расстояний");
    }

    JsonNode firstRow = distancesNode.get(0);
    if (firstRow == null || !firstRow.isArray() || firstRow.size() != destinations.size()) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Роутинг-сервис вернул матрицу неожиданного размера");
    }

    List<Double> distancesKm = new ArrayList<>(destinations.size());
    for (JsonNode distanceNode : firstRow) {
      if (distanceNode == null || distanceNode.isNull()) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Для части точек не удалось построить дорожный маршрут");
      }
      distancesKm.add(distanceNode.asDouble() / 1000.0);
    }
    return distancesKm;
  }

  private String buildCoordinates(RouteCoordinate source, List<RouteCoordinate> destinations) {
    StringBuilder builder = new StringBuilder(64);
    appendCoordinate(builder, source);
    for (RouteCoordinate destination : destinations) {
      builder.append(';');
      appendCoordinate(builder, destination);
    }
    return builder.toString();
  }

  private String buildDestinationsParam(int destinationsCount) {
    StringBuilder builder = new StringBuilder(destinationsCount * 2);
    for (int i = 0; i < destinationsCount; i++) {
      if (i > 0) {
        builder.append(';');
      }
      builder.append(i + 1);
    }
    return builder.toString();
  }

  private void appendCoordinate(StringBuilder builder, RouteCoordinate coordinate) {
    builder
        .append(formatCoordinate(coordinate.longitude()))
        .append(',')
        .append(formatCoordinate(coordinate.latitude()));
  }

  private String formatCoordinate(double value) {
    return String.format(Locale.US, "%.7f", value);
  }

  private String cacheKey(RouteCoordinate from, RouteCoordinate to) {
    return formatCoordinate(from.latitude()) + "|" + formatCoordinate(from.longitude())
        + "->"
        + formatCoordinate(to.latitude()) + "|" + formatCoordinate(to.longitude());
  }

  private Double readCache(String key) {
    CacheEntry<Double> entry = distanceCache.get(key);
    if (entry == null) {
      return null;
    }
    if (entry.expiresAt() <= System.currentTimeMillis()) {
      distanceCache.remove(key, entry);
      return null;
    }
    return entry.value();
  }

  private void writeCache(String key, Double value) {
    pruneExpiredEntries();
    distanceCache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + cacheTtlMs));

    while (distanceCache.size() > cacheMaxEntries) {
      String keyToEvict = distanceCache.keySet().stream().findFirst().orElse(null);
      if (keyToEvict == null) {
        return;
      }
      distanceCache.remove(keyToEvict);
    }
  }

  private void pruneExpiredEntries() {
    long now = System.currentTimeMillis();
    distanceCache.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
  }

  private record CacheEntry<T>(T value, long expiresAt) {
  }

  public record RouteCoordinate(double latitude, double longitude) {
  }
}
