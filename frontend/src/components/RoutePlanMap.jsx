import { memo, useEffect, useMemo, useRef, useState } from 'react';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { previewAutoAssignRouteGeometry } from '../api.js';
import { tripStyle } from '../utils/routeColors.js';
import {
  createTripKey,
  selectVisiblePlan
} from '../utils/routePlanPreview.js';

const transportNumberFormatter = new Intl.NumberFormat('ru-RU', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 1
});

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
      `border:2px solid rgba(255,255,255,0.96);background:${color};color:#ffffff;` +
      `font-size:12px;font-weight:700;line-height:1;` +
      `box-shadow:0 0 0 1px rgba(15,23,42,0.24),0 4px 10px rgba(17,24,39,0.24);` +
      `">${sequence}</span>`
  });
}

function coordinateKey(latitude, longitude) {
  return `${Number(latitude).toFixed(6)}:${Number(longitude).toFixed(6)}`;
}

function offsetMarkerPoint(latitude, longitude, occurrenceIndex) {
  if (!occurrenceIndex) {
    return [latitude, longitude];
  }

  const ringStep = Math.floor((occurrenceIndex - 1) / 6) + 1;
  const angle = ((occurrenceIndex - 1) % 6) * (Math.PI / 3);
  const radius = 0.00018 * ringStep;
  return [
    latitude + Math.sin(angle) * radius,
    longitude + Math.cos(angle) * radius
  ];
}

function stopOrderLine(point) {
  const orderIds = Array.isArray(point?.orderIds) ? point.orderIds : [];
  if (!orderIds.length && point?.orderId != null) {
    return `Заказ #${point.orderId}`;
  }
  if (orderIds.length <= 1) {
    return `Заказ ${orderIds.map((orderId) => `#${orderId}`).join(', ')}`;
  }
  return `Заказы: ${orderIds.map((orderId) => `#${orderId}`).join(', ')}`;
}

function countLabel(count, one, few, many) {
  const normalized = Number.isFinite(Number(count)) ? Math.max(0, Math.trunc(Number(count))) : 0;
  const mod10 = normalized % 10;
  const mod100 = normalized % 100;
  if (mod10 === 1 && mod100 !== 11) {
    return `${normalized} ${one}`;
  }
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
    return `${normalized} ${few}`;
  }
  return `${normalized} ${many}`;
}

function orderCountLabel(count) {
  return countLabel(count, 'заказ', 'заказа', 'заказов');
}

function formatTransportNumber(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return transportNumberFormatter.format(0);
  }
  return transportNumberFormatter.format(numeric);
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

function normalizeTripPoints(points) {
  if (!Array.isArray(points)) {
    return [];
  }

  const normalized = [];
  points.forEach((point) => {
    const latitude = Number(point?.latitude);
    const longitude = Number(point?.longitude);
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
      return;
    }
    const previous = normalized[normalized.length - 1];
    if (previous && previous.latitude === latitude && previous.longitude === longitude) {
      return;
    }
    normalized.push({ latitude, longitude });
  });
  return normalized;
}

function addStyledPolyline(layerTarget, layers, path, visual) {
  const halo = L.polyline(path, {
    color: '#ffffff',
    weight: visual.weight + 4,
    opacity: 0.9,
    lineCap: 'round',
    lineJoin: 'round'
  }).addTo(layerTarget);
  const polyline = L.polyline(path, {
    color: visual.color,
    weight: visual.weight,
    opacity: visual.opacity,
    dashArray: visual.dashArray,
    lineCap: 'round',
    lineJoin: 'round'
  }).addTo(layerTarget);

  layers.push(halo, polyline);
}

function buildTripGeometryRequests(plan) {
  if (!plan || !Array.isArray(plan.routes)) {
    return [];
  }

  const requests = [];
  plan.routes.forEach((route, routeIndex) => {
    const routeKey = route?.driverId || routeIndex;
    const tripSegments = Array.isArray(route?.trips)
      ? [...route.trips].sort((left, right) => (left.tripNumber || 1) - (right.tripNumber || 1))
      : [];

    tripSegments.forEach((trip) => {
      const points = normalizeTripPoints(
        [...(trip?.points || [])]
          .sort((left, right) => left.stopSequence - right.stopSequence)
          .map((point) => ({
            latitude: point?.latitude,
            longitude: point?.longitude
          }))
      );
      if (!points.length) {
        return;
      }
      requests.push({
        key: createTripKey(routeKey, trip?.tripNumber),
        points,
        returnsToDepot: Boolean(trip?.returnsToDepot)
      });
    });
  });
  return requests;
}

function fitMapToPlan(map, bounds, depotPoint, { animate = true } = {}) {
  if (!map) {
    return;
  }

  const transitionOptions = animate
    ? {
        animate: true,
        duration: 0.45,
        easeLinearity: 0.2
      }
    : {
        animate: false
      };

  if (Array.isArray(bounds) && bounds.length > 1) {
    map.fitBounds(L.latLngBounds(bounds), {
      padding: [32, 32],
      maxZoom: 15,
      ...transitionOptions
    });
    return;
  }

  map.setView(depotPoint, 12, {
    ...transitionOptions
  });
}

function buildStopPopupHtml(route, point) {
  const stopSequence = Number(point?.displayStopSequence) || Number(point?.stopSequence) || '?';
  const tripNumber = Number(point?.tripNumber) || 1;
  const tripStopCount = Number(point?.tripStopCount) || 0;
  const tripAssignedOrders = Number(point?.tripAssignedOrders) || Number(route?.assignedOrders) || 0;
  const orderCount = Number(point?.orderCount) || (point?.orderId != null ? 1 : 0);
  const legLabel = stopSequence === 1 ? 'От склада' : 'От предыдущей остановки';
  const distanceFromPreviousKm = Number(point?.distanceFromPreviousKm) || 0;
  const cumulativeDistanceKm = Number(point?.cumulativeDistanceKm) || distanceFromPreviousKm;
  const tripEstimatedRouteDistanceKm = Number(point?.tripEstimatedRouteDistanceKm) || 0;
  const tripWeightUtilizationPercent = Number(point?.tripWeightUtilizationPercent) || 0;
  const tripVolumeUtilizationPercent = Number(point?.tripVolumeUtilizationPercent) || 0;
  const tripTotalWeightKg = Number(point?.tripTotalWeightKg) || 0;
  const tripTotalVolumeM3 = Number(point?.tripTotalVolumeM3) || 0;
  const returnToDepotDistanceKm = Number(point?.returnToDepotDistanceKm) || 0;
  const returnsToDepot = Boolean(point?.returnsToDepot);
  const isLastStopInTrip = Boolean(point?.isLastStopInTrip);
  const selectionReason = point?.selectionReason
    ? escapeHtml(point.selectionReason)
    : 'Точка включена в маршрут.';

  const popupLines = [
    `<div style="font-weight:700;font-size:16px;margin-bottom:2px;">${escapeHtml(route.driverName)}</div>`,
    `<div style="color:#475569;margin-bottom:8px;">Рейс ${tripNumber}${tripStopCount ? ` · точка ${stopSequence} из ${tripStopCount}` : ''}</div>`,
    `<div style="margin-bottom:4px;"><strong>${escapeHtml(point.deliveryAddress)}</strong></div>`,
    `<div style="margin-bottom:4px;">${escapeHtml(stopOrderLine(point))}</div>`,
    `<div style="color:#475569;margin-bottom:8px;">На этой остановке: ${escapeHtml(orderCountLabel(orderCount))}. Всего в рейсе: ${escapeHtml(orderCountLabel(tripAssignedOrders))}.</div>`,
    `<div style="margin-bottom:2px;">${escapeHtml(legLabel)}: ${formatTransportNumber(distanceFromPreviousKm)} км</div>`,
    `<div style="margin-bottom:2px;">Накопленный путь к этой точке: ${formatTransportNumber(cumulativeDistanceKm)} км</div>`,
    `<div style="margin-bottom:2px;">Полный километраж рейса: ${formatTransportNumber(tripEstimatedRouteDistanceKm)} км</div>`,
    `<div style="margin-bottom:2px;">Загрузка рейса: ${formatTransportNumber(tripTotalWeightKg)} кг (${formatTransportNumber(tripWeightUtilizationPercent)}%) · ${formatTransportNumber(tripTotalVolumeM3)} м³ (${formatTransportNumber(tripVolumeUtilizationPercent)}%)</div>`
  ];

  if (returnsToDepot && isLastStopInTrip) {
    popupLines.push(
      `<div style="margin-bottom:2px;">Возврат на склад после этой точки: ${formatTransportNumber(returnToDepotDistanceKm)} км</div>`
    );
  }

  popupLines.push(
    `<div style="margin-top:8px;padding-top:8px;border-top:1px solid #e2e8f0;color:#334155;">Почему выбрана: ${selectionReason}</div>`
  );

  return `<div style="min-width:280px;max-width:340px;line-height:1.45;">${popupLines.join('')}</div>`;
}

function RoutePlanMap({ plan, token, visibleDriverId = 'all', visibleTripNumber = 'all' }) {
  const containerRef = useRef(null);
  const mapRef = useRef(null);
  const overlayLayerRef = useRef(null);
  const resizeTimeoutRef = useRef(0);
  const viewportTimeoutRef = useRef(0);
  const lastViewportPlanRef = useRef(null);
  const [tripGeometry, setTripGeometry] = useState({});
  const [geometryLoading, setGeometryLoading] = useState(false);
  const visiblePlan = useMemo(
    () => selectVisiblePlan(plan, visibleDriverId, visibleTripNumber),
    [plan, visibleDriverId, visibleTripNumber]
  );

  useEffect(() => {
    let cancelled = false;
    const controllers = new Set();
    const requests = buildTripGeometryRequests(plan);
    setTripGeometry({});

    if (!token || !requests.length) {
      setGeometryLoading(false);
      return undefined;
    }

    setGeometryLoading(true);
    let cursor = 0;
    const concurrency = Math.min(4, requests.length);

    const loadGeometry = async () => {
      while (!cancelled) {
        const currentIndex = cursor;
        cursor += 1;
        if (currentIndex >= requests.length) {
          return;
        }

        const request = requests[currentIndex];
        const controller = new AbortController();
        controllers.add(controller);
        try {
          const geometry = await previewAutoAssignRouteGeometry(
            token,
            request.points,
            {
              returnsToDepot: request.returnsToDepot,
              signal: controller.signal
            }
          );
          const normalizedGeometry = normalizeRoutePath(geometry);
          if (!cancelled && normalizedGeometry.length >= 2) {
            setTripGeometry((previous) => ({
              ...previous,
              [request.key]: normalizedGeometry
            }));
          }
        } catch (error) {
          if (!cancelled && !controller.signal.aborted) {
            console.warn('Unable to load road geometry for auto-assign trip', request.key, error);
          }
        } finally {
          controllers.delete(controller);
        }
      }
    };

    void Promise.allSettled(
      Array.from({ length: concurrency }, () => loadGeometry())
    ).finally(() => {
      if (!cancelled) {
        setGeometryLoading(false);
      }
    });

    return () => {
      cancelled = true;
      controllers.forEach((controller) => controller.abort());
      controllers.clear();
    };
  }, [plan, token]);

  useEffect(() => {
    if (!containerRef.current || mapRef.current) {
      return undefined;
    }

    const map = L.map(containerRef.current, {
      zoomControl: true,
      attributionControl: false,
      preferCanvas: true,
      zoomAnimation: true,
      fadeAnimation: true,
      markerZoomAnimation: true,
      zoomSnap: 0.25,
      zoomDelta: 0.5,
      wheelDebounceTime: 24
    }).setView([53.9, 30.33], 12);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19
    }).addTo(map);

    const overlayLayer = L.layerGroup().addTo(map);
    mapRef.current = map;
    overlayLayerRef.current = overlayLayer;

    let resizeObserver = null;
    const invalidateMapSize = () => {
      if (!mapRef.current) {
        return;
      }
      window.clearTimeout(resizeTimeoutRef.current);
      resizeTimeoutRef.current = window.setTimeout(() => {
        if (!mapRef.current) {
          return;
        }
        mapRef.current.invalidateSize({
          pan: false,
          debounceMoveend: true
        });
      }, 90);
    };

    if (typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(() => {
        invalidateMapSize();
      });
      resizeObserver.observe(containerRef.current);
    }

    return () => {
      window.clearTimeout(resizeTimeoutRef.current);
      window.clearTimeout(viewportTimeoutRef.current);
      resizeObserver?.disconnect();
      overlayLayerRef.current?.clearLayers();
      map.stop();
      map.remove();
      overlayLayerRef.current = null;
      mapRef.current = null;
      lastViewportPlanRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (!visiblePlan || !mapRef.current || !overlayLayerRef.current) {
      return undefined;
    }

    const map = mapRef.current;
    const overlayLayer = overlayLayerRef.current;
    overlayLayer.clearLayers();
    const layers = [];
    const depotPoint = [visiblePlan.depotLatitude, visiblePlan.depotLongitude];
    const depotMarker = L.circleMarker(depotPoint, {
      radius: 10,
      weight: 3,
      color: '#374151',
      fillColor: '#f8fafc',
      fillOpacity: 1
    })
      .bindPopup(`Логистический хаб: ${visiblePlan.depotLabel || 'База логистики'}`)
      .addTo(overlayLayer);
    layers.push(depotMarker);

    const bounds = [depotPoint];
    const markerOccurrences = new Map();
    visiblePlan.routes.forEach((route, routeIndex) => {
      const routeKey = route?.driverId || routeIndex;
      const colorIndex = Number.isInteger(route?.colorIndex) ? route.colorIndex : routeIndex;
      const orderedStops = [...(route?.displayStops || route?.points || [])].sort((left, right) => {
        const tripDiff = (Number(left?.tripNumber) || 1) - (Number(right?.tripNumber) || 1);
        if (tripDiff !== 0) {
          return tripDiff;
        }
        return (Number(left?.displayStopSequence) || Number(left?.stopSequence) || 0)
          - (Number(right?.displayStopSequence) || Number(right?.stopSequence) || 0);
      });
      if (!orderedStops.length) {
        return;
      }

      const tripSegments = Array.isArray(route?.trips) && route.trips.length
        ? [...route.trips].sort((left, right) => (left.tripNumber || 1) - (right.tripNumber || 1))
        : [];

      if (tripSegments.length) {
        tripSegments.forEach((trip) => {
          const tripKey = createTripKey(routeKey, trip?.tripNumber);
          const tripPath = tripGeometry[tripKey] || [];
          if (tripPath.length < 2) {
            return;
          }
          const visual = tripStyle(colorIndex, trip?.tripNumber || 1);
          addStyledPolyline(overlayLayer, layers, tripPath, visual);
          tripPath.forEach((point) => bounds.push(point));
        });
      } else {
        const routePath = normalizeRoutePath(route?.path);
        if (routePath.length >= 2) {
          const visual = tripStyle(colorIndex, 1);
          addStyledPolyline(overlayLayer, layers, routePath, visual);
          routePath.forEach((point) => bounds.push(point));
        }
      }

      orderedStops.forEach((point) => {
        const baseLatitude = Number(point?.latitude);
        const baseLongitude = Number(point?.longitude);
        const occurrenceKey = coordinateKey(baseLatitude, baseLongitude);
        const occurrenceIndex = markerOccurrences.get(occurrenceKey) || 0;
        markerOccurrences.set(occurrenceKey, occurrenceIndex + 1);

        const markerPoint = offsetMarkerPoint(baseLatitude, baseLongitude, occurrenceIndex);
        const markerColor = tripStyle(colorIndex, point.tripNumber || 1).color;
        const displayStopSequence = Number(point?.displayStopSequence) || Number(point?.stopSequence) || '?';
        const marker = L.marker(markerPoint, {
          icon: createStopOrderIcon(displayStopSequence, markerColor),
          keyboard: false
        })
          .bindPopup(buildStopPopupHtml(route, point))
          .addTo(overlayLayer);
        layers.push(marker);
        bounds.push(markerPoint);
      });
    });

    const shouldRefit = lastViewportPlanRef.current !== visiblePlan;
    window.clearTimeout(viewportTimeoutRef.current);
    viewportTimeoutRef.current = window.setTimeout(() => {
      if (!mapRef.current || mapRef.current !== map) {
        return;
      }

      map.invalidateSize({
        pan: false,
        debounceMoveend: true
      });

      if (shouldRefit) {
        map.stop();
        fitMapToPlan(map, bounds, depotPoint, {
          animate: true
        });
        lastViewportPlanRef.current = visiblePlan;
      }
    }, shouldRefit ? 120 : 0);

    return () => {
      window.clearTimeout(viewportTimeoutRef.current);
      layers.forEach((layer) => {
        overlayLayer.removeLayer(layer);
      });
    };
  }, [tripGeometry, visiblePlan]);

  return (
    <Box sx={{ position: 'relative' }}>
      <Box
        ref={containerRef}
        aria-label="Карта автопостроенных маршрутов"
        aria-describedby="route-plan-map-description"
        sx={{
          width: '100%',
          height: { xs: 280, md: 360 },
          borderRadius: 2,
          border: '1px solid',
          borderColor: 'divider',
          overflow: 'hidden',
          bgcolor: 'background.default'
        }}
      />
      {geometryLoading ? (
        <Box
          sx={{
            position: 'absolute',
            top: 10,
            right: 10,
            display: 'inline-flex',
            alignItems: 'center',
            gap: 0.75,
            px: 1,
            py: 0.5,
            borderRadius: 999,
            bgcolor: 'rgba(255,255,255,0.92)',
            border: '1px solid rgba(148,163,184,0.45)',
            boxShadow: '0 4px 14px rgba(15,23,42,0.08)',
            fontSize: 12,
            color: 'text.secondary'
          }}
        >
          <CircularProgress size={14} />
          <Box component="span">Маршруты по дорогам загружаются</Box>
        </Box>
      ) : null}
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
