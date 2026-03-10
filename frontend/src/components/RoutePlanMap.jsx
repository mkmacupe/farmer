import { memo, useEffect, useRef, useState } from 'react';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { previewAutoAssignRouteGeometry } from '../api.js';

const ROUTE_COLORS = ['#5a7fa8', '#b18a52', '#4f8a6d', '#8a78a5', '#b07a7a'];

function routeColor(routeIndex) {
  return ROUTE_COLORS[routeIndex % ROUTE_COLORS.length];
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

function normalizeRoutePath(path) {
  if (!Array.isArray(path)) {
    return [];
  }

  return path
    .map((point) => {
      if (Array.isArray(point) && point.length >= 2) {
        const [latitude, longitude] = point;
        if (Number.isFinite(latitude) && Number.isFinite(longitude)) {
          return [latitude, longitude];
        }
      }
      if (!point || typeof point !== 'object') {
        return null;
      }
      const latitude = Number(point.latitude);
      const longitude = Number(point.longitude);
      if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
        return null;
      }
      return [latitude, longitude];
    })
    .filter(Boolean);
}

function routeKey(route, routeIndex) {
  return route?.driverId ?? `route-${routeIndex}`;
}

function RoutePlanMap({ plan, token }) {
  const containerRef = useRef(null);
  const [routePaths, setRoutePaths] = useState({});
  const [geometryLoading, setGeometryLoading] = useState(false);
  const [geometryPrepared, setGeometryPrepared] = useState(false);

  useEffect(() => {
    if (!plan) {
      setRoutePaths({});
      setGeometryLoading(false);
      setGeometryPrepared(false);
      return undefined;
    }

    const initialPaths = {};
    const routesToLoad = [];
    plan.routes.forEach((route, routeIndex) => {
      const key = routeKey(route, routeIndex);
      const inlinePath = normalizeRoutePath(route?.path);
      if (inlinePath.length >= 2) {
        initialPaths[key] = inlinePath;
        return;
      }
      if (Array.isArray(route?.points) && route.points.length) {
        routesToLoad.push({ key, points: route.points });
      }
    });
    setRoutePaths(initialPaths);
    setGeometryPrepared(false);

    if (!token || !routesToLoad.length) {
      setGeometryLoading(false);
      setGeometryPrepared(true);
      return undefined;
    }

    let cancelled = false;
    setGeometryLoading(true);

    Promise.all(
      routesToLoad.map(async ({ key, points }) => {
        try {
          const path = await previewAutoAssignRouteGeometry(
            token,
            [...points].sort((left, right) => left.stopSequence - right.stopSequence)
          );
          return [key, normalizeRoutePath(path)];
        } catch {
          return [key, []];
        }
      })
    )
      .then((entries) => {
        if (cancelled) {
          return;
        }
        setRoutePaths((previous) => {
          const next = { ...previous };
          entries.forEach(([key, path]) => {
            if (path.length >= 2) {
              next[key] = path;
            }
          });
          return next;
        });
      })
      .finally(() => {
        if (!cancelled) {
          setGeometryLoading(false);
          setGeometryPrepared(true);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [plan, token]);

  useEffect(() => {
    if (!plan || !containerRef.current || !geometryPrepared) {
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
      const orderedStops = [...(route?.points || [])].sort((left, right) => left.stopSequence - right.stopSequence);
      if (!orderedStops.length) {
        return;
      }

      const routePath = routePaths[routeKey(route, routeIndex)] || normalizeRoutePath(route?.path);
      if (routePath.length >= 2) {
        const polyline = L.polyline(routePath, {
          color,
          weight: 4,
          opacity: 0.9
        }).addTo(map);
        layers.push(polyline);
        routePath.forEach((point) => bounds.push(point));
      }

      orderedStops.forEach((point) => {
        const markerPoint = [point.latitude, point.longitude];
        const marker = L.marker(markerPoint, {
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
        bounds.push(markerPoint);
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

    return () => {
      window.clearTimeout(timeoutId);
      layers.forEach((layer) => layer.remove());
      map.remove();
    };
  }, [geometryPrepared, plan, routePaths]);

  if (!geometryPrepared) {
    return (
      <Box
        sx={{
          width: '100%',
          height: { xs: 210, md: 250 },
          borderRadius: 2,
          border: '1px solid',
          borderColor: 'divider',
          bgcolor: 'background.default',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center'
        }}
      >
        <CircularProgress size={26} />
      </Box>
    );
  }

  return (
    <Box sx={{ position: 'relative' }}>
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
      {geometryLoading && (
        <Box
          sx={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            pointerEvents: 'none'
          }}
        >
          <Box
            sx={{
              px: 2,
              py: 1.25,
              borderRadius: 2,
              bgcolor: 'rgba(255,255,255,0.9)',
              boxShadow: 2
            }}
          >
            <Stack direction="row" spacing={1.25} alignItems="center">
              <CircularProgress size={18} />
            </Stack>
          </Box>
        </Box>
      )}
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

export default memo(RoutePlanMap);
