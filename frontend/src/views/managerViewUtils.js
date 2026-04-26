export const STATUS_LABELS = {
  CREATED: "Создан",
  APPROVED: "Одобрен",
  ASSIGNED: "Назначен",
  DELIVERED: "Доставлен",
};

export const REPORT_STATUS_ALL = "__ALL__";
export const DAY_MS = 24 * 60 * 60 * 1000;
export const MAX_DASHBOARD_DAYS = 31;
export const CATEGORY_COLORS = [
  "#5a7fa8",
  "#4f8a6d",
  "#b18a52",
  "#8a78a5",
  "#b07a7a",
  "#5c8f8a",
];
export const PRODUCT_PHOTO_URL_PATTERN = /^\/images\/products\/[a-z0-9-]+\.webp$/;
export const PRODUCT_PHOTO_AUTOGEN_PREFIX = "/images/products/product-";
export const PRODUCT_PHOTO_RANDOM_LENGTH = 6;

export function formatLocalDateValue(value) {
  const date = new Date(value);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function reportDateToApiValue(value) {
  const raw = String(value || "").trim();
  if (!raw) return null;

  const isoMatch = raw.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (isoMatch) {
    const [, year, month, day] = isoMatch;
    return isValidDateParts(year, month, day) ? raw : undefined;
  }

  const displayMatch = raw.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
  if (!displayMatch) {
    return undefined;
  }

  const [, day, month, year] = displayMatch;
  if (!isValidDateParts(year, month, day)) {
    return undefined;
  }
  return `${year}-${month}-${day}`;
}

export function normalizeReportDateDisplay(value) {
  const apiValue = reportDateToApiValue(value);
  if (!apiValue) {
    return "";
  }
  const [year, month, day] = apiValue.split("-");
  return `${day}/${month}/${year}`;
}

export function todayDateValue() {
  return formatLocalDateValue(new Date());
}

export function isMethodNotAllowedError(error) {
  const message = String(error?.message || "").toLowerCase();
  return message.includes("method not allowed") || message.includes("405");
}

export function statusLabel(status) {
  return STATUS_LABELS[status] || status || "-";
}

export function reportStatusLabel(status) {
  return status === REPORT_STATUS_ALL ? "Все статусы" : statusLabel(status);
}

export function formatDateTime(value) {
  if (!value) return "";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "";
  return parsed.toLocaleString("ru-RU");
}

function isValidDateParts(year, month, day) {
  const numericYear = Number(year);
  const numericMonth = Number(month);
  const numericDay = Number(day);
  if (
    !Number.isInteger(numericYear)
    || !Number.isInteger(numericMonth)
    || !Number.isInteger(numericDay)
    || numericMonth < 1
    || numericMonth > 12
    || numericDay < 1
    || numericDay > 31
  ) {
    return false;
  }

  const parsed = new Date(numericYear, numericMonth - 1, numericDay);
  return parsed.getFullYear() === numericYear
    && parsed.getMonth() === numericMonth - 1
    && parsed.getDate() === numericDay;
}

export function generateProductPhotoUrl() {
  const stamp = Date.now().toString(36);
  const randomSegment = Math.random()
    .toString(36)
    .slice(2, 2 + PRODUCT_PHOTO_RANDOM_LENGTH);
  return `${PRODUCT_PHOTO_AUTOGEN_PREFIX}${stamp}-${randomSegment}.webp`;
}

export function parseDateInput(value) {
  if (!value) return null;
  const parsed = new Date(`${value}T00:00:00`);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

export function startOfDay(value) {
  const date = new Date(value);
  date.setHours(0, 0, 0, 0);
  return date;
}

export function dayKey(value) {
  return formatLocalDateValue(startOfDay(value));
}

export function formatDayLabel(value) {
  return value.toLocaleDateString("ru-RU", {
    day: "2-digit",
    month: "2-digit",
  });
}

export function orderTimestamp(order) {
  const candidates = [
    order.createdAt,
    order.updatedAt,
    order.approvedAt,
    order.assignedAt,
    order.deliveredAt,
  ];
  for (const candidate of candidates) {
    if (!candidate) continue;
    const parsed = new Date(candidate);
    if (!Number.isNaN(parsed.getTime())) {
      return parsed.getTime();
    }
  }
  return null;
}
