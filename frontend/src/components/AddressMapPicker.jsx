import { useEffect, useRef, useState } from "react";
import * as L from "leaflet/dist/leaflet-src.esm.js";
import "leaflet/dist/leaflet.css";

const DEFAULT_CENTER = { lat: 53.8948, lng: 30.3312 };
const DEFAULT_ZOOM = 12;
const PICKED_ZOOM = 15;

function parseCoordinate(value, min, max) {
  if (value == null) {
    return null;
  }
  if (typeof value === "string" && value.trim() === "") {
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
    color: "#4f8a6d",
    weight: 2,
    fillColor: "#b18a52",
    fillOpacity: 0.9,
  }).addTo(map);
}

function setMapInteractive(map, enabled) {
  const method = enabled ? "enable" : "disable";
  map.dragging?.[method]?.();
  map.touchZoom?.[method]?.();
  map.doubleClickZoom?.[method]?.();
  map.scrollWheelZoom?.[method]?.();
  map.boxZoom?.[method]?.();
  map.keyboard?.[method]?.();
  map.tap?.[method]?.();
}

export default function AddressMapPicker({ latitude, longitude, onSelect }) {
  const containerRef = useRef(null);
  const mapRef = useRef(null);
  const markerRef = useRef(null);
  const onSelectRef = useRef(onSelect);
  const mapEnabledRef = useRef(false);
  const [mapEnabled, setMapEnabled] = useState(false);

  useEffect(() => {
    onSelectRef.current = onSelect;
  }, [onSelect]);

  useEffect(() => {
    mapEnabledRef.current = mapEnabled;
    if (mapRef.current) {
      setMapInteractive(mapRef.current, mapEnabled);
    }
  }, [mapEnabled]);

  useEffect(() => {
    if (!containerRef.current || mapRef.current) {
      return undefined;
    }

    const lat = parseCoordinate(latitude, -90, 90);
    const lng = parseCoordinate(longitude, -180, 180);
    const hasValidCoords = lat != null && lng != null;
    const center = hasValidCoords
      ? [lat, lng]
      : [DEFAULT_CENTER.lat, DEFAULT_CENTER.lng];

    const map = L.map(containerRef.current, {
      zoomControl: true,
      scrollWheelZoom: true,
      attributionControl: false,
    }).setView(center, hasValidCoords ? PICKED_ZOOM : DEFAULT_ZOOM);
    setMapInteractive(map, mapEnabledRef.current);

    mapRef.current = map;

    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
    }).addTo(map);

    if (hasValidCoords) {
      markerRef.current = createMarker(map, center);
    }

    const handleMapClick = (event) => {
      if (!mapEnabledRef.current) {
        return;
      }
      const selectedPoint = [event.latlng.lat, event.latlng.lng];
      if (!markerRef.current) {
        markerRef.current = createMarker(map, selectedPoint);
      } else {
        markerRef.current.setLatLng(selectedPoint);
      }
      map.setView(selectedPoint, Math.max(PICKED_ZOOM, map.getZoom()), {
        animate: true,
      });
      onSelectRef.current?.(
        formatCoordinate(event.latlng.lat),
        formatCoordinate(event.latlng.lng),
      );
    };

    map.on("click", handleMapClick);
    const invalidateTimeoutId = window.setTimeout(() => {
      if (mapRef.current === map) {
        map.invalidateSize();
      }
    }, 0);

    return () => {
      window.clearTimeout(invalidateTimeoutId);
      map.off("click", handleMapClick);
      map.remove();
      mapRef.current = null;
      markerRef.current = null;
    };
  }, [latitude, longitude]);

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
    <div className="map-picker" style={{ position: "relative" }}>
      <div
        ref={containerRef}
        className="map-picker-canvas"
        role="application"
        aria-label="Карта выбора адреса"
        style={{
          width: "100%",
          height: "clamp(360px, 50vh, 520px)",
          minHeight: "360px",
          position: "relative",
          zIndex: 0,
          borderRadius: "12px",
          border: "1px solid #e4e4e7",
          overflow: "hidden",
          background: "#f6f6f6",
          opacity: 1,
        }}
      />
      {!mapEnabled && (
        <div
          style={{
            position: "absolute",
            inset: 0,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            borderRadius: "12px",
            background: "rgba(17, 24, 39, 0.18)",
            backdropFilter: "blur(1px)",
            zIndex: 20,
            pointerEvents: "auto",
          }}
        >
          <button
            type="button"
            onClick={() => setMapEnabled(true)}
            style={{
              border: "1px solid #2E5B4E",
              background: "#2E5B4E",
              color: "#ffffff",
              borderRadius: "8px",
              padding: "10px 16px",
              fontSize: "0.875rem",
              fontWeight: 600,
              cursor: "pointer",
            }}
          >
            Открыть карту для выбора точки
          </button>
        </div>
      )}
    </div>
  );
}
