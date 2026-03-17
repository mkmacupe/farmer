function normalizeLookupValue(value) {
  return String(value ?? "").trim().toLocaleLowerCase("ru-RU");
}

function toPositiveInt(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return 0;
  }
  return Math.floor(numeric);
}

function uniqueLabels(values) {
  return [...new LinkedHashSet(values)];
}

class LinkedHashSet extends Set {
  add(value) {
    if (!this.has(value)) {
      super.add(value);
    }
    return this;
  }
}

function buildCatalogLookup(products) {
  const byId = new Map();
  const byPhotoUrl = new Map();
  const byName = new Map();

  (Array.isArray(products) ? products : []).forEach((product) => {
    const productId = Number(product?.id);
    if (Number.isFinite(productId) && productId > 0) {
      byId.set(productId, product);
    }

    const photoKey = normalizeLookupValue(product?.photoUrl);
    if (photoKey && !byPhotoUrl.has(photoKey)) {
      byPhotoUrl.set(photoKey, product);
    }

    const nameKey = normalizeLookupValue(product?.name);
    if (nameKey && !byName.has(nameKey)) {
      byName.set(nameKey, product);
    }
  });

  return { byId, byPhotoUrl, byName };
}

function resolveCurrentProduct(item, lookup) {
  const productId = Number(item?.productId);
  if (Number.isFinite(productId) && productId > 0 && lookup.byId.has(productId)) {
    return lookup.byId.get(productId);
  }

  const photoKey = normalizeLookupValue(item?.photoUrl);
  if (photoKey && lookup.byPhotoUrl.has(photoKey)) {
    return lookup.byPhotoUrl.get(photoKey);
  }

  const nameKey = normalizeLookupValue(item?.productName);
  if (nameKey && lookup.byName.has(nameKey)) {
    return lookup.byName.get(nameKey);
  }

  return null;
}

function itemLabel(item, fallbackProduct) {
  return item?.productName || fallbackProduct?.name || `Товар #${item?.productId ?? "?"}`;
}

export function reconcileRequestedItems(requestedItems, catalogProducts) {
  const lookup = buildCatalogLookup(catalogProducts);
  const mergedByProductId = new Map();
  const unavailable = [];
  const quantityAdjusted = [];
  const remapped = [];

  for (const item of Array.isArray(requestedItems) ? requestedItems : []) {
    const requestedQuantity = toPositiveInt(item?.quantity);
    if (requestedQuantity <= 0) {
      continue;
    }

    const product = resolveCurrentProduct(item, lookup);
    const label = itemLabel(item, product);
    if (!product) {
      unavailable.push(label);
      continue;
    }

    const productId = Number(product.id);
    const stockQuantity = Math.max(0, toPositiveInt(product.stockQuantity));
    const existingEntry = mergedByProductId.get(productId);
    const alreadyRequested = existingEntry?.quantity ?? 0;
    const remainingStock = Math.max(0, stockQuantity - alreadyRequested);

    if (remainingStock <= 0) {
      quantityAdjusted.push(label);
      continue;
    }

    const resolvedQuantity = Math.min(requestedQuantity, remainingStock);
    if (resolvedQuantity < requestedQuantity) {
      quantityAdjusted.push(label);
    }

    if (Number(item?.productId) !== productId) {
      remapped.push(label);
    }

    mergedByProductId.set(productId, {
      product,
      quantity: alreadyRequested + resolvedQuantity,
    });
  }

  return {
    items: [...mergedByProductId.values()],
    unavailable: uniqueLabels(unavailable),
    quantityAdjusted: uniqueLabels(quantityAdjusted),
    remapped: uniqueLabels(remapped),
  };
}

export function summarizeProductNames(names, limit = 3) {
  const values = uniqueLabels(Array.isArray(names) ? names.filter(Boolean) : []);
  if (!values.length) {
    return "";
  }
  if (values.length <= limit) {
    return values.join(", ");
  }
  return `${values.slice(0, limit).join(", ")} и ещё ${values.length - limit}`;
}
