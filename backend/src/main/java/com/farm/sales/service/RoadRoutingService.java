package com.farm.sales.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.ProxySelector;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Duration;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RoadRoutingService {
  private static final long DEFAULT_CACHE_TTL_MS = 15 * 60 * 1000L;
  private static final int DEFAULT_CACHE_MAX_ENTRIES = 10_000;
  private static final long DEFAULT_FAILURE_COOLDOWN_MS = 60_000L;
  private static final double ROUTE_POINT_EPSILON = 0.00001;
  private static final double MAX_GEOMETRY_CONNECTOR_METERS = 60.0;
  private final String baseUrl;
  private final String userAgent;
  private final RestClient restClient;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final Duration requestTimeout;
  private final long cacheTtlMs;
  private final int cacheMaxEntries;
  private final int maxTableCoordinates;
  private final long failureCooldownMs;
  private final Map<String, CacheEntry<Double>> distanceCache = new ConcurrentHashMap<>();
  private final Map<String, CacheEntry<List<RouteCoordinate>>> geometryCache = new ConcurrentHashMap<>();
  private final AtomicLong tableRoutingUnavailableUntil = new AtomicLong(0L);
  private final AtomicLong geometryRoutingUnavailableUntil = new AtomicLong(0L);

  @Autowired
  public RoadRoutingService(
      @Value("${app.routing.osrm-base-url:https://router.project-osrm.org}") String baseUrl,
      @Value("${app.routing.user-agent:FarmSalesCourseProject/1.0}") String userAgent,
      @Value("${app.routing.connect-timeout-ms:3000}") int connectTimeoutMs,
      @Value("${app.routing.read-timeout-ms:6000}") int readTimeoutMs,
      @Value("${app.routing.cache-ttl-ms:" + DEFAULT_CACHE_TTL_MS + "}") long cacheTtlMs,
      @Value("${app.routing.cache-max-entries:" + DEFAULT_CACHE_MAX_ENTRIES + "}") int cacheMaxEntries,
      @Value("${app.routing.max-table-coordinates:50}") int maxTableCoordinates,
      @Value("${app.routing.failure-cooldown-ms:" + DEFAULT_FAILURE_COOLDOWN_MS + "}") long failureCooldownMs
  ) {
    String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.baseUrl = normalizedBaseUrl;
    this.userAgent = userAgent;
    this.restClient = null;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(Math.max(connectTimeoutMs, 100)))
        .proxy(noProxySelector())
        .build();
    this.objectMapper = new ObjectMapper();
    this.requestTimeout = Duration.ofMillis(Math.max(readTimeoutMs, 100));
    this.cacheTtlMs = Math.max(1_000L, cacheTtlMs);
    this.cacheMaxEntries = Math.max(100, cacheMaxEntries);
    this.maxTableCoordinates = Math.max(2, maxTableCoordinates);
    this.failureCooldownMs = Math.max(1_000L, failureCooldownMs);
  }

  RoadRoutingService(RestClient restClient, long cacheTtlMs, int cacheMaxEntries, int maxTableCoordinates) {
    this("https://router.project-osrm.org", restClient, cacheTtlMs, cacheMaxEntries, maxTableCoordinates, DEFAULT_FAILURE_COOLDOWN_MS);
  }

  RoadRoutingService(RestClient restClient,
                     long cacheTtlMs,
                     int cacheMaxEntries,
                     int maxTableCoordinates,
                     long failureCooldownMs) {
    this("https://router.project-osrm.org", restClient, cacheTtlMs, cacheMaxEntries, maxTableCoordinates, failureCooldownMs);
  }

  RoadRoutingService(String baseUrl,
                     RestClient restClient,
                     long cacheTtlMs,
                     int cacheMaxEntries,
                     int maxTableCoordinates,
                     long failureCooldownMs) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.userAgent = "FarmSalesCourseProject/1.0";
    this.restClient = restClient;
    this.httpClient = null;
    this.objectMapper = new ObjectMapper();
    this.requestTimeout = Duration.ofSeconds(10);
    this.cacheTtlMs = Math.max(1_000L, cacheTtlMs);
    this.cacheMaxEntries = Math.max(100, cacheMaxEntries);
    this.maxTableCoordinates = Math.max(2, maxTableCoordinates);
    this.failureCooldownMs = Math.max(1_000L, failureCooldownMs);
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

  public List<List<Double>> drivingDistanceMatrixKm(List<RouteCoordinate> sources, List<RouteCoordinate> destinations) {
    if (sources == null || sources.isEmpty() || destinations == null || destinations.isEmpty()) {
      return List.of();
    }

    List<List<Double>> results = new ArrayList<>(sources.size());
    List<Integer> uncachedSourceIndexes = new ArrayList<>();
    for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
      RouteCoordinate source = sources.get(sourceIndex);
      List<Double> row = new ArrayList<>(destinations.size());
      boolean hasMisses = false;
      for (RouteCoordinate destination : destinations) {
        if (source.equals(destination)) {
          row.add(0.0);
          continue;
        }

        Double cached = readCache(cacheKey(source, destination));
        if (cached != null) {
          row.add(cached);
        } else {
          row.add(null);
          hasMisses = true;
        }
      }
      results.add(row);
      if (hasMisses) {
        uncachedSourceIndexes.add(sourceIndex);
      }
    }

    if (uncachedSourceIndexes.isEmpty()) {
      return results.stream().map(List::copyOf).toList();
    }

    int maxDestinationsPerRequest = Math.max(1, maxTableCoordinates - 1);
    int destinationFromIndex = 0;
    while (destinationFromIndex < destinations.size()) {
      int destinationToIndex = Math.min(destinationFromIndex + maxDestinationsPerRequest, destinations.size());
      List<RouteCoordinate> destinationChunk = destinations.subList(destinationFromIndex, destinationToIndex);
      int maxSourcesPerRequest = Math.max(1, maxTableCoordinates - destinationChunk.size());

      int sourceFromIndex = 0;
      while (sourceFromIndex < uncachedSourceIndexes.size()) {
        int sourceToIndex = Math.min(sourceFromIndex + maxSourcesPerRequest, uncachedSourceIndexes.size());
        List<Integer> sourceIndexesChunk = uncachedSourceIndexes.subList(sourceFromIndex, sourceToIndex);
        List<RouteCoordinate> sourceChunk = sourceIndexesChunk.stream()
            .map(sources::get)
            .toList();
        List<List<Double>> chunkMatrix = requestTableDistanceMatrix(sourceChunk, destinationChunk);

        for (int rowIndex = 0; rowIndex < sourceIndexesChunk.size(); rowIndex++) {
          int sourceIndex = sourceIndexesChunk.get(rowIndex);
          RouteCoordinate source = sources.get(sourceIndex);
          List<Double> row = results.get(sourceIndex);
          List<Double> matrixRow = chunkMatrix.get(rowIndex);
          for (int offset = 0; offset < destinationChunk.size(); offset++) {
            int destinationIndex = destinationFromIndex + offset;
            if (row.get(destinationIndex) != null) {
              continue;
            }
            RouteCoordinate destination = destinationChunk.get(offset);
            double distanceKm = matrixRow.get(offset);
            row.set(destinationIndex, distanceKm);
            writeCache(cacheKey(source, destination), distanceKm);
          }
        }

        sourceFromIndex = sourceToIndex;
      }

      destinationFromIndex = destinationToIndex;
    }

    return results.stream().map(List::copyOf).toList();
  }

  public List<RouteCoordinate> drivingRouteGeometry(List<RouteCoordinate> waypoints) {
    List<RouteCoordinate> normalizedWaypoints = normalizeWaypoints(waypoints);
    if (normalizedWaypoints.size() < 2) {
      return normalizedWaypoints;
    }

    String cacheKey = geometryCacheKey(normalizedWaypoints);
    List<RouteCoordinate> cached = readGeometryCache(cacheKey);
    if (cached != null) {
      return cached;
    }

    ensureGeometryRoutingAvailable();

    JsonNode payload;
    try {
      payload = executeJsonGet(URI.create(baseUrl + "/route/v1/driving/" + buildCoordinates(normalizedWaypoints)
          + "?overview=full&geometries=geojson&steps=false"));
    } catch (RuntimeException exception) {
      throw markGeometryFailure("Не удалось получить дорожную геометрию маршрута: " + exception.getMessage(), exception);
    }

    if (payload == null || !"Ok".equals(payload.path("code").asText())) {
      throw markGeometryFailure("Роутинг-сервис вернул некорректную геометрию маршрута");
    }

    JsonNode routeCoordinates = payload.path("routes").path(0).path("geometry").path("coordinates");
    if (!routeCoordinates.isArray() || routeCoordinates.isEmpty()) {
      throw markGeometryFailure("Роутинг-сервис не вернул линию маршрута");
    }

    List<RouteCoordinate> geometry = new ArrayList<>(routeCoordinates.size());
    for (JsonNode routePoint : routeCoordinates) {
      if (routePoint == null || !routePoint.isArray() || routePoint.size() < 2) {
        continue;
      }
      double longitude = routePoint.get(0).asDouble(Double.NaN);
      double latitude = routePoint.get(1).asDouble(Double.NaN);
      if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
        continue;
      }
      geometry.add(new RouteCoordinate(latitude, longitude));
    }

    if (geometry.size() < 2) {
      throw markGeometryFailure("Роутинг-сервис вернул пустую геометрию маршрута");
    }

    RouteCoordinate exactStart = normalizedWaypoints.getFirst();
    RouteCoordinate exactEnd = normalizedWaypoints.getLast();
    if (!pointsEqual(geometry.getFirst(), exactStart)
        && distanceMeters(geometry.getFirst(), exactStart) <= MAX_GEOMETRY_CONNECTOR_METERS) {
      geometry.add(0, exactStart);
    }
    if (!pointsEqual(geometry.getLast(), exactEnd)
        && distanceMeters(geometry.getLast(), exactEnd) <= MAX_GEOMETRY_CONNECTOR_METERS) {
      geometry.add(exactEnd);
    }

    List<RouteCoordinate> resolvedGeometry = List.copyOf(geometry);
    geometryRoutingUnavailableUntil.set(0L);
    writeGeometryCache(cacheKey, resolvedGeometry);
    return resolvedGeometry;
  }

  private List<Double> requestTableDistances(RouteCoordinate source, List<RouteCoordinate> destinations) {
    ensureTableRoutingAvailable();
    String coordinates = buildCoordinates(source, destinations);
    String destinationsParam = buildDestinationsParam(destinations.size());

    JsonNode payload;
    try {
      payload = executeJsonGet(URI.create(baseUrl + "/table/v1/driving/" + coordinates
          + "?sources=0&destinations=" + destinationsParam
          + "&annotations=distance"));
    } catch (RuntimeException exception) {
      throw markTableFailure("Не удалось получить дорожные расстояния: " + exception.getMessage(), exception);
    }

    if (payload == null || !"Ok".equals(payload.path("code").asText())) {
      throw markTableFailure("Роутинг-сервис вернул некорректный ответ");
    }

    JsonNode distancesNode = payload.path("distances");
    if (!distancesNode.isArray() || distancesNode.isEmpty()) {
      throw markTableFailure("Роутинг-сервис не вернул матрицу расстояний");
    }

    JsonNode firstRow = distancesNode.get(0);
    if (firstRow == null || !firstRow.isArray() || firstRow.size() != destinations.size()) {
      throw markTableFailure("Роутинг-сервис вернул матрицу неожиданного размера");
    }

    List<Double> distancesKm = new ArrayList<>(destinations.size());
    for (JsonNode distanceNode : firstRow) {
      if (distanceNode == null || distanceNode.isNull()) {
        throw markTableFailure("Для части точек не удалось построить дорожный маршрут");
      }
      distancesKm.add(distanceNode.asDouble() / 1000.0);
    }
    tableRoutingUnavailableUntil.set(0L);
    return distancesKm;
  }

  private List<List<Double>> requestTableDistanceMatrix(List<RouteCoordinate> sources, List<RouteCoordinate> destinations) {
    ensureTableRoutingAvailable();
    String coordinates = buildCoordinates(sources, destinations);
    String sourcesParam = buildIndexParam(0, sources.size());
    String destinationsParam = buildIndexParam(sources.size(), destinations.size());

    JsonNode payload;
    try {
      payload = executeJsonGet(URI.create(baseUrl + "/table/v1/driving/" + coordinates
          + "?sources=" + sourcesParam
          + "&destinations=" + destinationsParam
          + "&annotations=distance"));
    } catch (RuntimeException exception) {
      throw markTableFailure("Не удалось получить матрицу дорожных расстояний: " + exception.getMessage(), exception);
    }

    if (payload == null || !"Ok".equals(payload.path("code").asText())) {
      throw markTableFailure("Роутинг-сервис вернул некорректную матрицу расстояний");
    }

    JsonNode distancesNode = payload.path("distances");
    if (!distancesNode.isArray() || distancesNode.size() != sources.size()) {
      throw markTableFailure("Роутинг-сервис не вернул матрицу ожидаемого размера");
    }

    List<List<Double>> matrix = new ArrayList<>(sources.size());
    for (JsonNode rowNode : distancesNode) {
      if (rowNode == null || !rowNode.isArray() || rowNode.size() != destinations.size()) {
        throw markTableFailure("Роутинг-сервис вернул матрицу неожиданного размера");
      }
      List<Double> row = new ArrayList<>(destinations.size());
      for (JsonNode distanceNode : rowNode) {
        if (distanceNode == null || distanceNode.isNull()) {
          throw markTableFailure("Для части точек не удалось построить дорожный маршрут");
        }
        row.add(distanceNode.asDouble() / 1000.0);
      }
      matrix.add(List.copyOf(row));
    }

    tableRoutingUnavailableUntil.set(0L);
    return List.copyOf(matrix);
  }

  private void ensureTableRoutingAvailable() {
    long unavailableUntil = tableRoutingUnavailableUntil.get();
    if (unavailableUntil > System.currentTimeMillis()) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Дорожный роутинг временно недоступен");
    }
  }

  private void ensureGeometryRoutingAvailable() {
    long unavailableUntil = geometryRoutingUnavailableUntil.get();
    if (unavailableUntil > System.currentTimeMillis()) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Дорожный роутинг временно недоступен");
    }
  }

  private ResponseStatusException markTableFailure(String message) {
    tableRoutingUnavailableUntil.set(System.currentTimeMillis() + failureCooldownMs);
    return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
  }

  private ResponseStatusException markTableFailure(String message, Exception exception) {
    tableRoutingUnavailableUntil.set(System.currentTimeMillis() + failureCooldownMs);
    return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message, exception);
  }

  private ResponseStatusException markGeometryFailure(String message) {
    geometryRoutingUnavailableUntil.set(System.currentTimeMillis() + failureCooldownMs);
    return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
  }

  private ResponseStatusException markGeometryFailure(String message, Exception exception) {
    geometryRoutingUnavailableUntil.set(System.currentTimeMillis() + failureCooldownMs);
    return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message, exception);
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

  private String buildCoordinates(List<RouteCoordinate> sources, List<RouteCoordinate> destinations) {
    StringBuilder builder = new StringBuilder(Math.max(64, (sources.size() + destinations.size()) * 20));
    boolean first = true;
    for (RouteCoordinate source : sources) {
      if (!first) {
        builder.append(';');
      }
      appendCoordinate(builder, source);
      first = false;
    }
    for (RouteCoordinate destination : destinations) {
      if (!first) {
        builder.append(';');
      }
      appendCoordinate(builder, destination);
      first = false;
    }
    return builder.toString();
  }

  private String buildCoordinates(List<RouteCoordinate> waypoints) {
    StringBuilder builder = new StringBuilder(Math.max(64, waypoints.size() * 20));
    for (int index = 0; index < waypoints.size(); index++) {
      if (index > 0) {
        builder.append(';');
      }
      appendCoordinate(builder, waypoints.get(index));
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

  private String buildIndexParam(int startIndex, int count) {
    StringBuilder builder = new StringBuilder(Math.max(4, count * 3));
    for (int index = 0; index < count; index++) {
      if (index > 0) {
        builder.append(';');
      }
      builder.append(startIndex + index);
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

  private String geometryCacheKey(List<RouteCoordinate> waypoints) {
    StringBuilder builder = new StringBuilder(Math.max(64, waypoints.size() * 20));
    for (int index = 0; index < waypoints.size(); index++) {
      if (index > 0) {
        builder.append("->");
      }
      RouteCoordinate waypoint = waypoints.get(index);
      builder.append(formatCoordinate(waypoint.latitude()))
          .append('|')
          .append(formatCoordinate(waypoint.longitude()));
    }
    return builder.toString();
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

  private List<RouteCoordinate> readGeometryCache(String key) {
    CacheEntry<List<RouteCoordinate>> entry = geometryCache.get(key);
    if (entry == null) {
      return null;
    }
    if (entry.expiresAt() <= System.currentTimeMillis()) {
      geometryCache.remove(key, entry);
      return null;
    }
    return entry.value();
  }

  private void writeGeometryCache(String key, List<RouteCoordinate> value) {
    pruneExpiredEntries();
    geometryCache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + cacheTtlMs));

    while (geometryCache.size() > cacheMaxEntries) {
      String keyToEvict = geometryCache.keySet().stream().findFirst().orElse(null);
      if (keyToEvict == null) {
        return;
      }
      geometryCache.remove(keyToEvict);
    }
  }

  private void pruneExpiredEntries() {
    long now = System.currentTimeMillis();
    distanceCache.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    geometryCache.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
  }

  private JsonNode executeJsonGet(URI uri) {
    if (httpClient != null) {
      HttpRequest request = HttpRequest.newBuilder(uri)
          .header("User-Agent", userAgent)
          .timeout(requestTimeout)
          .GET()
          .build();
      try {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
          throw new IllegalStateException("Routing endpoint responded with HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
      } catch (IOException exception) {
        throw new IllegalStateException("Routing endpoint I/O failure: " + exception.getMessage(), exception);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Routing endpoint call interrupted: " + exception.getMessage(), exception);
      }
    }

    if (restClient == null) {
      throw new IllegalStateException("No routing HTTP client configured");
    }

    try {
      return restClient.get()
          .uri(uri)
          .retrieve()
          .body(JsonNode.class);
    } catch (RestClientException exception) {
      throw new IllegalStateException("Routing endpoint client failure", exception);
    }
  }

  private ProxySelector noProxySelector() {
    return new ProxySelector() {
      @Override
      public List<java.net.Proxy> select(URI uri) {
        return List.of(java.net.Proxy.NO_PROXY);
      }

      @Override
      public void connectFailed(URI uri, java.net.SocketAddress sa, IOException ioe) {
        // No-op: routing requests are best-effort and callers already handle failures upstream.
      }
    };
  }

  private List<RouteCoordinate> normalizeWaypoints(List<RouteCoordinate> waypoints) {
    if (waypoints == null || waypoints.isEmpty()) {
      return List.of();
    }

    List<RouteCoordinate> normalized = new ArrayList<>(waypoints.size());
    RouteCoordinate previous = null;
    for (RouteCoordinate waypoint : waypoints) {
      if (waypoint == null
          || !Double.isFinite(waypoint.latitude())
          || !Double.isFinite(waypoint.longitude())) {
        continue;
      }
      if (previous != null && pointsEqual(previous, waypoint)) {
        continue;
      }
      normalized.add(waypoint);
      previous = waypoint;
    }
    return List.copyOf(normalized);
  }

  private boolean pointsEqual(RouteCoordinate left, RouteCoordinate right) {
    return Math.abs(left.latitude() - right.latitude()) <= ROUTE_POINT_EPSILON
        && Math.abs(left.longitude() - right.longitude()) <= ROUTE_POINT_EPSILON;
  }

  private double distanceMeters(RouteCoordinate left, RouteCoordinate right) {
    double latFactor = 111_320.0;
    double avgLatRadians = ((left.latitude() + right.latitude()) / 2.0) * (Math.PI / 180.0);
    double lonFactor = Math.cos(avgLatRadians) * 111_320.0;
    double latDelta = (left.latitude() - right.latitude()) * latFactor;
    double lonDelta = (left.longitude() - right.longitude()) * lonFactor;
    return Math.hypot(latDelta, lonDelta);
  }

  private record CacheEntry<T>(T value, long expiresAt) {
  }

  public record RouteCoordinate(double latitude, double longitude) {
  }
}
