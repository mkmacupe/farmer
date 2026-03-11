import { memo, useEffect, useRef } from 'react';
import Box from '@mui/material/Box';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

const ROUTE_COLORS = ['#5a7fa8', '#b18a52', '#4f8a6d', '#8a78a5', '#b07a7a'];

function routeColor(routeIndex) {
  return ROUTE_COLORS[routeIndex % ROUTE_COLORS.length];
}

function tintColor(hexColor, mixRatio) {
  const normalized = String(hexColor || '').replace('#', '');
  if (!/^[0-9a-f]{6}$/i.test(normalized)) {
    return hexColor;
  }
  const ratio = Math.max(0, Math.min(0.75, mixRatio));
  const red = parseInt(normalized.slice(0, 2), 16);
  const green = parseInt(normalized.slice(2, 4), 16);
  const blue = parseInt(normalized.slice(4, 6), 16);
  const blend = (channel) => Math.round(channel + (255 - channel) * ratio);
  return `rgb(${blend(red)}, ${blend(green)}, ${blend(blue)})`;
}

function tripColor(routeIndex, tripNumber = 1) {
  const baseColor = routeColor(routeIndex);
  if (!Number.isFinite(Number(tripNumber)) || Number(tripNumber) <= 1) {
    return baseColor;
  }
  const tintRatio = Math.min(0.24 + (Number(tripNumber) - 2) * 0.12, 0.6);
  return tintColor(baseColor, tintRatio);
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
      const baseColor = routeColor(routeIndex);
      const orderedStops = [...(route?.points || [])].sort((left, right) => left.stopSequence - right.stopSequence);
      if (!orderedStops.length) {
        return;
      }

      const tripSegments = Array.isArray(route?.trips) && route.trips.length
        ? [...route.trips].sort((left, right) => (left.tripNumber || 1) - (right.tripNumber || 1))
        : [];

      if (tripSegments.length) {
        tripSegments.forEach((trip) => {
          const tripPath = normalizeRoutePath(trip?.path);
          if (tripPath.length < 2) {
            return;
          }
          const segmentColor = tripColor(routeIndex, trip?.tripNumber || 1);
          const polyline = L.polyline(tripPath, {
            color: segmentColor,
            weight: 4,
            opacity: trip?.tripNumber > 1 ? 0.78 : 0.92
          }).addTo(map);
          layers.push(polyline);
          tripPath.forEach((point) => bounds.push(point));
        });
      } else {
        const routePath = normalizeRoutePath(route?.path);
        if (routePath.length >= 2) {
          const polyline = L.polyline(routePath, {
            color: baseColor,
            weight: 4,
            opacity: 0.9
          }).addTo(map);
          layers.push(polyline);
          routePath.forEach((point) => bounds.push(point));
        }
      }

      orderedStops.forEach((point) => {
        const markerPoint = [point.latitude, point.longitude];
        const markerColor = tripColor(routeIndex, point.tripNumber || 1);
        const marker = L.marker(markerPoint, {
          icon: createStopOrderIcon(point.stopSequence, markerColor),
          keyboard: false
        })
          .bindPopup(
            `<strong>${escapeHtml(route.driverName)}</strong><br/>` +
            `Рейс ${Number(point.tripNumber) || 1}<br/>` +
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
  }, [plan]);

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
