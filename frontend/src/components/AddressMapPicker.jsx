import { useEffect, useRef } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

const DEFAULT_CENTER = { lat: 53.8948, lng: 30.3312 };
const DEFAULT_ZOOM = 12;
const PICKED_ZOOM = 15;

function parseCoordinate(value, min, max) {
  if (value == null) {
    return null;
  }
  if (typeof value === 'string' && value.trim() === '') {
    return null;
  }
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric < min || numeric > max) {
    return null;
  }
  return numeric;
}

function formatCoordinate(value) {
  return value.toFixed(6);
}

function createMarker(map, position) {
  return L.circleMarker(position, {
    radius: 8,
    color: '#2e7d32',
    weight: 2,
    fillColor: '#d84315',
    fillOpacity: 0.9
  }).addTo(map);
}

export default function AddressMapPicker({ latitude, longitude, onSelect }) {
  const containerRef = useRef(null);
  const mapRef = useRef(null);
  const markerRef = useRef(null);
  const onSelectRef = useRef(onSelect);

  useEffect(() => {
    onSelectRef.current = onSelect;
  }, [onSelect]);

  useEffect(() => {
    if (!containerRef.current || mapRef.current) {
      return undefined;
    }

    const lat = parseCoordinate(latitude, -90, 90);
    const lng = parseCoordinate(longitude, -180, 180);
    const hasValidCoords = lat != null && lng != null;
    const center = hasValidCoords ? [lat, lng] : [DEFAULT_CENTER.lat, DEFAULT_CENTER.lng];

    const map = L.map(containerRef.current, {
      zoomControl: true,
      scrollWheelZoom: true
    }).setView(center, hasValidCoords ? PICKED_ZOOM : DEFAULT_ZOOM);

    mapRef.current = map;

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; участники OpenStreetMap'
    }).addTo(map);

    markerRef.current = createMarker(map, center);
    if (!hasValidCoords) {
      onSelectRef.current?.(formatCoordinate(DEFAULT_CENTER.lat), formatCoordinate(DEFAULT_CENTER.lng));
    }

    const handleMapClick = (event) => {
      const selectedPoint = [event.latlng.lat, event.latlng.lng];
      if (!markerRef.current) {
        markerRef.current = createMarker(map, selectedPoint);
      } else {
        markerRef.current.setLatLng(selectedPoint);
      }
      map.setView(selectedPoint, Math.max(PICKED_ZOOM, map.getZoom()), { animate: true });
      onSelectRef.current?.(formatCoordinate(event.latlng.lat), formatCoordinate(event.latlng.lng));
    };

    map.on('click', handleMapClick);
    setTimeout(() => map.invalidateSize(), 0);

    return () => {
      map.off('click', handleMapClick);
      map.remove();
      mapRef.current = null;
      markerRef.current = null;
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) {
      return;
    }

    const lat = parseCoordinate(latitude, -90, 90);
    const lng = parseCoordinate(longitude, -180, 180);

    if (lat == null || lng == null) {
      if (markerRef.current) {
        markerRef.current.remove();
        markerRef.current = null;
      }
      return;
    }

    const point = [lat, lng];
    if (!markerRef.current) {
      markerRef.current = createMarker(map, point);
    } else {
      markerRef.current.setLatLng(point);
    }
    map.panTo(point, { animate: false });
  }, [latitude, longitude]);

  return (
    <div className="map-picker">
      <div
        ref={containerRef}
        className="map-picker-canvas"
        role="application"
        aria-label="Карта выбора адреса"
        style={{
          width: '100%',
          height: 'clamp(360px, 50vh, 520px)',
          minHeight: '360px',
          borderRadius: '12px',
          border: '1px solid #e4e4e7',
          overflow: 'hidden',
          background: '#f6f6f6'
        }}
      />
      <div className="header-meta">
        Нажмите на карту, чтобы поставить метку. Широта и долгота заполнятся автоматически.
      </div>
    </div>
  );
}
