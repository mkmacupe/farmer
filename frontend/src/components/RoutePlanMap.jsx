import { memo, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import L from 'leaflet';
import 'leaflet-edgebuffer';
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

function getStopOrderIcon(cache, stopSequence, color) {
  const cacheKey = `${color}:${stopSequence}`;
  const cachedIcon = cache.get(cacheKey);
  if (cachedIcon) {
    return cachedIcon;
  }

  const icon = createStopOrderIcon(stopSequence, color);
  cache.set(cacheKey, icon);
  return icon;
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

function buildVisiblePlanKey(visiblePlan, visibleDriverId, visibleTripNumber) {
  if (!visiblePlan || !Array.isArray(visiblePlan.routes)) {
    return `${visibleDriverId}:${visibleTripNumber}:empty`;
  }

  const routeKey = visiblePlan.routes
    .map((route, routeIndex) => {
      const routeId = route?.driverId ?? routeIndex;
      const routeStops = Array.isArray(route?.displayStops || route?.points)
        ? (route.displayStops || route.points)
        : [];
      const stopKey = routeStops
        .map((point) => `${point?.orderId ?? 'x'}:${point?.tripNumber ?? 1}:${point?.displayStopSequence ?? point?.stopSequence ?? 0}`)
        .join(',');
      const tripKey = Array.isArray(route?.trips)
        ? route.trips
            .map((trip) => `${trip?.tripNumber ?? 1}:${Array.isArray(trip?.points) ? trip.points.length : 0}`)
            .join(',')
        : 'no-trips';
      return `${routeId}|${stopKey}|${tripKey}`;
    })
    .join('||');

  return `${visibleDriverId}:${visibleTripNumber}:${routeKey}`;
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

function fitMapToPlan(map, bounds, depotPoint, { animate = false } = {}) {
  if (!map) {
    return;
  }

  const transitionOptions = animate
    ? {
        animate: true,
        duration: 0.24,
        easeLinearity: 0.3
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

function StopDetailsPanel({ selectedStop, onClose }) {
  if (!selectedStop) {
    return null;
  }

  if (selectedStop.type === 'depot') {
    return (
      <Box
        sx={{
          width: { xs: '100%', md: 300 },
          flexShrink: 0,
          height: { xs: 'auto', md: 360 },
          overflow: 'auto',
          borderRadius: 2,
          border: '1px solid',
          borderColor: 'divider',
          bgcolor: 'background.paper',
          boxShadow: '0 10px 24px rgba(15,23,42,0.08)',
          p: 2
        }}
      >
        <Box
          sx={{
            display: 'flex',
            alignItems: 'flex-start',
            justifyContent: 'space-between',
            gap: 1.5,
            mb: 1.5
          }}
        >
          <Box sx={{ minWidth: 0 }}>
            <Box sx={{ fontSize: 18, fontWeight: 700, lineHeight: 1.25 }}>
              Логистический хаб
            </Box>
            <Box sx={{ mt: 0.5, color: 'text.secondary', lineHeight: 1.35 }}>
              Старт и завершение рейсов
            </Box>
          </Box>
          <Box
            component="button"
            type="button"
            aria-label="Закрыть подробности точки"
            onClick={onClose}
            sx={{
              border: 0,
              bgcolor: 'transparent',
              color: 'text.secondary',
              cursor: 'pointer',
              fontSize: 20,
              lineHeight: 1,
              p: 0,
              flexShrink: 0
            }}
          >
            ×
          </Box>
        </Box>

        <Box sx={{ fontSize: 15, fontWeight: 700, lineHeight: 1.35, mb: 1 }}>
          {selectedStop.label || 'База логистики'}
        </Box>

        <Box sx={{ color: 'text.secondary', lineHeight: 1.45 }}>
          Отсюда начинается построение всех рейсов. Если рейс требует возврата, маршрут также завершится в этой точке.
        </Box>
      </Box>
    );
  }

  const point = selectedStop.point;
  const stopSequence = Number(point?.displayStopSequence) || Number(point?.stopSequence) || '?';
  const tripNumber = Number(point?.tripNumber) || 1;
  const tripStopCount = Number(point?.tripStopCount) || 0;
  const tripAssignedOrders = Number(point?.tripAssignedOrders) || Number(selectedStop?.routeAssignedOrders) || 0;
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
  const selectionReason = point?.selectionReason || 'Точка включена в маршрут.';
  const stopOrders = Array.isArray(point?.orders) && point.orders.length
    ? point.orders
    : (point?.orderId != null ? [{ orderId: point.orderId, items: Array.isArray(point?.items) ? point.items : [] }] : []);

  return (
    <Box
      sx={{
        width: { xs: '100%', md: 300 },
        flexShrink: 0,
        height: { xs: 'auto', md: 360 },
        overflow: 'auto',
        borderRadius: 2,
        border: '1px solid',
        borderColor: 'divider',
        bgcolor: 'background.paper',
        boxShadow: '0 10px 24px rgba(15,23,42,0.08)',
        p: 2
      }}
    >
      <Box
        sx={{
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
          gap: 1.5,
          mb: 1.5
        }}
      >
        <Box sx={{ minWidth: 0 }}>
          <Box sx={{ fontSize: 18, fontWeight: 700, lineHeight: 1.25 }}>
            {selectedStop.driverName}
          </Box>
          <Box sx={{ mt: 0.5, color: 'text.secondary', lineHeight: 1.35 }}>
            Рейс {tripNumber}{tripStopCount ? ` · точка ${stopSequence} из ${tripStopCount}` : ''}
          </Box>
        </Box>
        <Box
          component="button"
          type="button"
          aria-label="Закрыть подробности точки"
          onClick={onClose}
          sx={{
            border: 0,
            bgcolor: 'transparent',
            color: 'text.secondary',
            cursor: 'pointer',
            fontSize: 20,
            lineHeight: 1,
            p: 0,
            flexShrink: 0
          }}
        >
          ×
        </Box>
      </Box>

      <Box sx={{ fontSize: 15, fontWeight: 700, lineHeight: 1.35, mb: 1 }}>
        {point?.deliveryAddress || 'Адрес не указан'}
      </Box>

      <Box sx={{ display: 'grid', gap: 0.9, color: 'text.primary', lineHeight: 1.42 }}>
        <Box>{stopOrderLine(point)}</Box>
        <Box sx={{ color: 'text.secondary' }}>
          На этой остановке: {orderCountLabel(orderCount)}. Всего в рейсе: {orderCountLabel(tripAssignedOrders)}.
        </Box>
        <Box>{legLabel}: {formatTransportNumber(distanceFromPreviousKm)} км</Box>
        <Box>Накопленный путь к этой точке: {formatTransportNumber(cumulativeDistanceKm)} км</Box>
        <Box>Полный километраж рейса: {formatTransportNumber(tripEstimatedRouteDistanceKm)} км</Box>
        <Box>
          Загрузка рейса: {formatTransportNumber(tripTotalWeightKg)} кг ({formatTransportNumber(tripWeightUtilizationPercent)}%) ·{' '}
          {formatTransportNumber(tripTotalVolumeM3)} м3 ({formatTransportNumber(tripVolumeUtilizationPercent)}%)
        </Box>
        {returnsToDepot && isLastStopInTrip ? (
          <Box>Возврат на склад после этой точки: {formatTransportNumber(returnToDepotDistanceKm)} км</Box>
        ) : null}
      </Box>

      {stopOrders.length ? (
        <Box
          sx={{
            mt: 1.5,
            pt: 1.5,
            borderTop: '1px solid',
            borderColor: 'divider',
            display: 'grid',
            gap: 1
          }}
        >
          <Box sx={{ fontSize: 14, fontWeight: 700, lineHeight: 1.35 }}>
            {stopOrders.length > 1 ? 'Состав заказов' : 'Состав заказа'}
          </Box>
          {stopOrders.map((order) => (
            <Box
              key={order.orderId ?? 'order'}
              sx={{
                p: 1,
                borderRadius: 1.5,
                bgcolor: 'background.default',
                border: '1px solid',
                borderColor: 'divider'
              }}
            >
              {Array.isArray(order.items) && order.items.length ? (
                <Box sx={{ display: 'grid', gap: 0.45 }}>
                  {order.items.map((item, itemIndex) => (
                    <Box
                      key={`${order.orderId ?? 'order'}:${item.productId ?? itemIndex}`}
                      sx={{ color: 'text.secondary', lineHeight: 1.35 }}
                    >
                      {item.productName || 'Без названия'} × {Number(item.quantity) || 0}
                    </Box>
                  ))}
                </Box>
              ) : (
                <Box sx={{ color: 'text.secondary', lineHeight: 1.35 }}>
                  Состав заказа недоступен.
                </Box>
              )}
            </Box>
          ))}
        </Box>
      ) : null}

      <Box
        sx={{
          mt: 1.5,
          pt: 1.5,
          borderTop: '1px solid',
          borderColor: 'divider',
          color: 'text.secondary',
          lineHeight: 1.45
        }}
      >
        Почему выбрана: {selectionReason}
      </Box>
    </Box>
  );
}

function RoutePlanMap({ plan, token, visibleDriverId = 'all', visibleTripNumber = 'all' }) {
  const containerRef = useRef(null);
  const mapRef = useRef(null);
  const overlayLayerRef = useRef(null);
  const iconCacheRef = useRef(new Map());
  const resizeTimeoutRef = useRef(0);
  const viewportTimeoutRef = useRef(0);
  const lastViewportPlanRef = useRef(null);
  const [tripGeometry, setTripGeometry] = useState({});
  const [geometryLoading, setGeometryLoading] = useState(false);
  const [baseTilesLoaded, setBaseTilesLoaded] = useState(false);
  const [viewportReadyKey, setViewportReadyKey] = useState('');
  const [pathsVisible, setPathsVisible] = useState(false);
  const [selectedStop, setSelectedStop] = useState(null);
  const visiblePlan = useMemo(
    () => selectVisiblePlan(plan, visibleDriverId, visibleTripNumber),
    [plan, visibleDriverId, visibleTripNumber]
  );
  const geometryRequests = useMemo(
    () => buildTripGeometryRequests(visiblePlan),
    [visiblePlan]
  );
  const pendingGeometryRequests = useMemo(
    () => geometryRequests.filter((request) => !tripGeometry[request.key]),
    [geometryRequests, tripGeometry]
  );
  const visiblePlanKey = useMemo(
    () => buildVisiblePlanKey(visiblePlan, visibleDriverId, visibleTripNumber),
    [visibleDriverId, visiblePlan, visibleTripNumber]
  );

  useLayoutEffect(() => {
    setSelectedStop(null);
  }, [visiblePlanKey]);

  useEffect(() => {
    setTripGeometry({});
  }, [plan]);

  useEffect(() => {
    let cancelled = false;
    const controllers = new Set();
    const nextGeometry = {};
    setSelectedStop(null);

    if (!token || !pendingGeometryRequests.length) {
      setGeometryLoading(false);
      return undefined;
    }

    setGeometryLoading(true);
    let cursor = 0;
    const concurrency = Math.min(3, pendingGeometryRequests.length);

    const loadGeometry = async () => {
      while (!cancelled) {
        const currentIndex = cursor;
        cursor += 1;
        if (currentIndex >= pendingGeometryRequests.length) {
          return;
        }

        const request = pendingGeometryRequests[currentIndex];
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
            nextGeometry[request.key] = normalizedGeometry;
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
        setTripGeometry((prev) => ({ ...prev, ...nextGeometry }));
        setGeometryLoading(false);
      }
    });

    return () => {
      cancelled = true;
      controllers.forEach((controller) => controller.abort());
      controllers.clear();
    };
  }, [pendingGeometryRequests, token]);

  useEffect(() => {
    if (!containerRef.current || mapRef.current) {
      return undefined;
    }

    const map = L.map(containerRef.current, {
      zoomControl: true,
      attributionControl: false,
      zoomAnimation: true,
      fadeAnimation: true,
      markerZoomAnimation: true,
      zoomSnap: 0.5,
      zoomDelta: 1,
      wheelDebounceTime: 20,
      wheelPxPerZoomLevel: 30,
      preferCanvas: false
    }).setView([53.9, 30.33], 12);

    let disposed = false;
    const tileLayer = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      keepBuffer: 8,
      updateWhenIdle: true,
      updateWhenZooming: false,
      edgeBufferTiles: 2
    }).addTo(map);
    const markTilesLoaded = () => {
      if (!disposed) {
        setBaseTilesLoaded(true);
      }
    };
    tileLayer.once('load', markTilesLoaded);
    if (typeof tileLayer.isLoading === 'function' && !tileLayer.isLoading()) {
      markTilesLoaded();
    }

    const overlayLayer = L.layerGroup().addTo(map);
    mapRef.current = map;
    overlayLayerRef.current = overlayLayer;
    const clearSelectedStop = () => {
      setSelectedStop(null);
    };
    map.on('click', clearSelectedStop);

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
      disposed = true;
      window.clearTimeout(resizeTimeoutRef.current);
      window.clearTimeout(viewportTimeoutRef.current);
      resizeObserver?.disconnect();
      tileLayer.off('load', markTilesLoaded);
      map.off('click', clearSelectedStop);
      overlayLayerRef.current?.clearLayers();
      map.stop();
      map.remove();
      overlayLayerRef.current = null;
      mapRef.current = null;
      lastViewportPlanRef.current = null;
    };
  }, []);

  useEffect(() => {
    setPathsVisible(false);

    if (!visiblePlan?.routes?.length || !baseTilesLoaded || geometryLoading) {
      return undefined;
    }
    if (viewportReadyKey !== visiblePlanKey) {
      return undefined;
    }

    setPathsVisible(true);
  }, [baseTilesLoaded, geometryLoading, viewportReadyKey, visiblePlan, visiblePlanKey]);

  useEffect(() => {
    if (!visiblePlan || !mapRef.current || !overlayLayerRef.current) {
      return undefined;
    }

    const map = mapRef.current;
    const overlayLayer = overlayLayerRef.current;
    let settleTimeoutId = 0;
    let viewportListener = null;
    overlayLayer.clearLayers();
    const layers = [];
    const depotPoint = [visiblePlan.depotLatitude, visiblePlan.depotLongitude];
    const depotMarker = L.circleMarker(depotPoint, {
      radius: 10,
      weight: 3,
      color: '#374151',
      fillColor: '#f8fafc',
      fillOpacity: 1,
      bubblingMouseEvents: false
    })
      .addTo(overlayLayer);
    depotMarker.on('click', () => {
      setSelectedStop({
        type: 'depot',
        label: visiblePlan.depotLabel || 'База логистики'
      });
    });
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
          const tripPath = tripGeometry[tripKey] || normalizeRoutePath(trip?.path);
          if (tripPath.length < 2) {
            return;
          }
          if (!pathsVisible) {
            return;
          }
          const visual = tripStyle(colorIndex, trip?.tripNumber || 1);
          addStyledPolyline(overlayLayer, layers, tripPath, visual);
        });
      } else {
        const routePath = normalizeRoutePath(route?.path);
        if (pathsVisible && routePath.length >= 2) {
          const visual = tripStyle(colorIndex, 1);
          addStyledPolyline(overlayLayer, layers, routePath, visual);
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
          icon: getStopOrderIcon(iconCacheRef.current, displayStopSequence, markerColor),
          keyboard: false
        })
          .addTo(overlayLayer);
        marker.on('click', () => {
          setSelectedStop({
            driverName: route?.driverName || 'Водитель',
            routeAssignedOrders: route?.assignedOrders,
            point
          });
        });
        layers.push(marker);
        bounds.push(markerPoint);
      });
    });

    const shouldRefit = lastViewportPlanRef.current !== visiblePlanKey;
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
        let settled = false;
        const markViewportReady = () => {
          if (
            settled ||
            !mapRef.current ||
            mapRef.current !== map ||
            lastViewportPlanRef.current !== visiblePlanKey
          ) {
            return;
          }
          settled = true;
          map.off('moveend', markViewportReady);
          setViewportReadyKey(visiblePlanKey);
        };

        map.stop();
        map.once('moveend', markViewportReady);
        viewportListener = markViewportReady;
        fitMapToPlan(map, bounds, depotPoint, {
          animate: false
        });
        lastViewportPlanRef.current = visiblePlanKey;
        settleTimeoutId = window.setTimeout(markViewportReady, 40);
        return;
      }

      setViewportReadyKey(visiblePlanKey);
    }, shouldRefit ? 24 : 0);

    return () => {
      window.clearTimeout(viewportTimeoutRef.current);
      window.clearTimeout(settleTimeoutId);
      if (viewportListener) {
        map.off('moveend', viewportListener);
      }
      layers.forEach((layer) => {
        overlayLayer.removeLayer(layer);
      });
    };
  }, [pathsVisible, tripGeometry, visiblePlan, visiblePlanKey]);

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: { xs: 'column', md: 'row' },
        alignItems: 'stretch',
        gap: 2
      }}
    >
      {selectedStop ? (
        <StopDetailsPanel
          selectedStop={selectedStop}
          onClose={() => {
            setSelectedStop(null);
          }}
        />
      ) : null}
      <Box sx={{ position: 'relative', flex: 1, minWidth: 0 }}>
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
            bgcolor: 'background.default',
            visibility: geometryLoading ? 'hidden' : 'visible'
          }}
        />
        {geometryLoading ? (
          <Box
            sx={{
              position: 'absolute',
              inset: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              px: 2,
              borderRadius: 2,
              border: '1px solid',
              borderColor: 'divider',
              bgcolor: 'rgba(248,250,252,0.96)',
              backdropFilter: 'blur(2px)'
            }}
          >
            <Box
              sx={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: 1.25,
                textAlign: 'center',
                color: 'text.secondary'
              }}
            >
              <CircularProgress size={28} />
              <Box component="span" sx={{ fontSize: 13, lineHeight: 1.45 }}>
                Прокладываем маршруты между точками
              </Box>
            </Box>
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
          Визуальная схема маршрутов. Подробности точки открываются в отдельной карточке рядом с картой.
        </Box>
      </Box>
    </Box>
  );
}

export default memo(RoutePlanMap);
