import { memo, useEffect, useRef } from 'react';
import Box from '@mui/material/Box';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

const ROUTE_COLORS = ['#5a7fa8', '#b18a52', '#4f8a6d', '#8a78a5', '#b07a7a'];
const OSRM_PUBLIC_ROUTE_API = 'https://router.project-osrm.org/route/v1/driving';
const ROAD_ROUTE_SEGMENT_TIMEOUT_MS = 1800;
const ROAD_ROUTE_CACHE_TTL_MS = 10 * 60 * 1000;
const ROAD_ROUTE_CACHE_MAX_ITEMS = 180;
const ROAD_ROUTE_CACHE = new Map();
const ROAD_ROUTE_PENDING = new Map();
const ROUTE_POINT_EPSILON = 0.00001;

function routeColor(routeIndex) {
  return ROUTE_COLORS[routeIndex % ROUTE_COLORS.length];
}

function routeCacheKey(points) {
  return points
    .map(([lat, lon]) => `${Number(lat).toFixed(5)},${Number(lon).toFixed(5)}`)
    .join('|');
}

function normalizeRoutePoints(rawPoints) {
  const convertedPoints = rawPoints
    .map((coordinate) => {
      if (!Array.isArray(coordinate) || coordinate.length < 2) {
        return null;
      }
      const [lon, lat] = coordinate;
      if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
        return null;
      }
      return [lat, lon];
    })
    .filter(Boolean);

  return convertedPoints.length >= 2 ? convertedPoints : null;
}

function pointsEqual(left, right) {
  if (!Array.isArray(left) || !Array.isArray(right) || left.length < 2 || right.length < 2) {
    return false;
  }
  return (
    Math.abs(Number(left[0]) - Number(right[0])) <= ROUTE_POINT_EPSILON &&
    Math.abs(Number(left[1]) - Number(right[1])) <= ROUTE_POINT_EPSILON
  );
}

function createStopOrderIcon(stopSequence, color) {
  const sequence = Number.isFinite(Number(stopSequence))
    ? Math.max(1, Math.trunc(Number(stopSequence)))
    : '?';

  return L.divIcon({
    className: '',
    iconSize: [26, 26],
    iconAnchor: [13, 13],
    popupAnchor: [0, -14],
    html:
      `<span style="` +
      `display:flex;align-items:center;justify-content:center;` +
      `width:26px;height:26px;border-radius:50%;` +
      `border:2px solid ${color};background:#ffffff;color:#1f2937;` +
      `font-size:12px;font-weight:700;line-height:1;` +
      `box-shadow:0 1px 4px rgba(17,24,39,0.2);` +
      `">${sequence}</span>`
  });
}

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function pruneExpiredRoutes(now = Date.now()) {
  for (const [key, entry] of ROAD_ROUTE_CACHE.entries()) {
    if (entry.expiresAt <= now) {
      ROAD_ROUTE_CACHE.delete(key);
    }
  }
}

function readCachedRoute(key) {
  const entry = ROAD_ROUTE_CACHE.get(key);
  if (!entry) {
    return null;
  }
  if (entry.expiresAt <= Date.now()) {
    ROAD_ROUTE_CACHE.delete(key);
    return null;
  }
  return entry.points;
}

function writeCachedRoute(key, points) {
  pruneExpiredRoutes();
  if (ROAD_ROUTE_CACHE.has(key)) {
    ROAD_ROUTE_CACHE.delete(key);
  }
  ROAD_ROUTE_CACHE.set(key, {
    points,
    expiresAt: Date.now() + ROAD_ROUTE_CACHE_TTL_MS
  });

  while (ROAD_ROUTE_CACHE.size > ROAD_ROUTE_CACHE_MAX_ITEMS) {
    const oldestKey = ROAD_ROUTE_CACHE.keys().next().value;
    if (!oldestKey) {
      break;
    }
    ROAD_ROUTE_CACHE.delete(oldestKey);
  }
}

async function requestRoadRoute(points, signal, timeoutMs) {
  if (points.length < 2) {
    return null;
  }

  const key = routeCacheKey(points);
  const cachedPoints = readCachedRoute(key);
  if (cachedPoints) {
    return cachedPoints;
  }

  const pending = ROAD_ROUTE_PENDING.get(key);
  if (pending) {
    return pending;
  }

  const requestPromise = (async () => {
    const requestAbort = new AbortController();
    const timeoutId = window.setTimeout(() => {
      requestAbort.abort();
    }, timeoutMs);
    const abortFromParent = () => requestAbort.abort();
    signal?.addEventListener('abort', abortFromParent, { once: true });

    const waypointSequence = points.map(([lat, lon]) => `${lon},${lat}`).join(';');
    const query = new URLSearchParams({
      overview: 'simplified',
      geometries: 'geojson',
      steps: 'false'
    });

    try {
      const response = await fetch(`${OSRM_PUBLIC_ROUTE_API}/${waypointSequence}?${query.toString()}`, {
        signal: requestAbort.signal
      });
      if (!response.ok) {
        throw new Error(`OSRM request failed with status ${response.status}`);
      }

      const payload = await response.json();
      const geometryPoints = payload?.routes?.[0]?.geometry?.coordinates;
      if (!Array.isArray(geometryPoints) || !geometryPoints.length) {
        throw new Error('OSRM empty geometry');
      }

      const normalizedPoints = normalizeRoutePoints(geometryPoints);
      if (!normalizedPoints) {
        throw new Error('OSRM malformed geometry');
      }

      const exactPolyline = [...normalizedPoints];
      const exactStart = points[0];
      const exactEnd = points[points.length - 1];

      if (!pointsEqual(exactPolyline[0], exactStart)) {
        exactPolyline.unshift(exactStart);
      }
      if (!pointsEqual(exactPolyline[exactPolyline.length - 1], exactEnd)) {
        exactPolyline.push(exactEnd);
      }

      writeCachedRoute(key, exactPolyline);
      return exactPolyline;
    } finally {
      window.clearTimeout(timeoutId);
      signal?.removeEventListener('abort', abortFromParent);
    }
  })();

  ROAD_ROUTE_PENDING.set(key, requestPromise);
  try {
    return await requestPromise;
  } finally {
    ROAD_ROUTE_PENDING.delete(key);
  }
}

async function resolveRoadPolylines(points, signal) {
  if (points.length < 2) {
    return [];
  }

  const segmentTasks = [];
  for (let index = 0; index < points.length - 1; index += 1) {
    const from = points[index];
    const to = points[index + 1];
    segmentTasks.push(
      requestRoadRoute([from, to], signal, ROAD_ROUTE_SEGMENT_TIMEOUT_MS).catch(() => [from, to])
    );
  }

  const segments = (await Promise.all(segmentTasks)).filter(
    (segment) => Array.isArray(segment) && segment.length >= 2
  );
  return segments;
}

function RoutePlanMap({ plan }) {
  const containerRef = useRef(null);

  useEffect(() => {
    if (!plan || !containerRef.current) {
      return undefined;
    }

    const map = L.map(containerRef.current, {
      zoomControl: true,
      attributionControl: false,
      preferCanvas: true
    }).setView([plan.depotLatitude, plan.depotLongitude], 12);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19
    }).addTo(map);

    const abortController = new AbortController();
    const routeDrawTasks = [];
    const layers = [];
    const depotPoint = [plan.depotLatitude, plan.depotLongitude];
    const depotMarker = L.circleMarker(depotPoint, {
      radius: 10,
      weight: 3,
      color: '#374151',
      fillColor: '#f8fafc',
      fillOpacity: 1
    })
      .bindPopup(`Старт: ${plan.depotLabel || 'База логистики'}`)
      .addTo(map);
    layers.push(depotMarker);

    const bounds = [depotPoint];
    plan.routes.forEach((route, routeIndex) => {
      const color = routeColor(routeIndex);
      const orderedStops = [...route.points].sort((left, right) => left.stopSequence - right.stopSequence);
      if (!orderedStops.length) {
        return;
      }

      const polylinePoints = [depotPoint, ...orderedStops.map((point) => [point.latitude, point.longitude])];
      const routeTask = resolveRoadPolylines(polylinePoints, abortController.signal)
        .then((roadPolylineSegments) => {
          if (abortController.signal.aborted) {
            return;
          }
          roadPolylineSegments.forEach((segment) => {
            const polyline = L.polyline(segment, {
              color,
              weight: 4,
              opacity: 0.9
            }).addTo(map);
            layers.push(polyline);
          });
        })
        .catch(() => undefined);
      routeDrawTasks.push(routeTask);

      orderedStops.forEach((point) => {
        const marker = L.marker([point.latitude, point.longitude], {
          icon: createStopOrderIcon(point.stopSequence, color),
          keyboard: false
        })
          .bindPopup(
            `<strong>${escapeHtml(route.driverName)}</strong><br/>` +
            `Точка #${point.stopSequence}<br/>` +
            `Заказ #${point.orderId}<br/>` +
            `${escapeHtml(point.deliveryAddress)}<br/>` +
            `Плечо: ${point.distanceFromPreviousKm} км`
          )
          .addTo(map);
        layers.push(marker);
        bounds.push([point.latitude, point.longitude]);
      });
    });

    if (bounds.length > 1) {
      map.fitBounds(bounds, { padding: [24, 24] });
    } else {
      map.setView(depotPoint, 12);
    }

    const timeoutId = window.setTimeout(() => {
      map.invalidateSize();
    }, 60);

    Promise.allSettled(routeDrawTasks).finally(() => {
      if (abortController.signal.aborted) {
        return;
      }
      map.invalidateSize();
    });

    return () => {
      abortController.abort();
      window.clearTimeout(timeoutId);
      layers.forEach((layer) => layer.remove());
      map.remove();
    };
  }, [plan]);

  return (
    <Box>
      <Box
        ref={containerRef}
        aria-label="Карта автопостроенных маршрутов"
        aria-describedby="route-plan-map-description"
        sx={{
          width: '100%',
          height: { xs: 210, md: 250 },
          borderRadius: 2,
          border: '1px solid',
          borderColor: 'divider',
          overflow: 'hidden',
          bgcolor: 'background.default'
        }}
      />
      <Box
        id="route-plan-map-description"
        sx={{
          position: 'absolute',
          width: 1,
          height: 1,
          p: 0,
          m: -1,
          overflow: 'hidden',
          clip: 'rect(0 0 0 0)',
          whiteSpace: 'nowrap',
          border: 0
        }}
      >
        Визуальная схема маршрутов. Подробности точек доступны в таблице маршрутного плана.
      </Box>
    </Box>
  );
}

export default RoutePlanMap;
