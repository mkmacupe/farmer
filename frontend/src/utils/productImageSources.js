export function buildGeneratedProductImageUrl(productId) {
  const normalizedId = Number(productId);
  if (!Number.isInteger(normalizedId) || normalizedId <= 0) {
    return "";
  }
  return `/images/products/product_${String(normalizedId).padStart(4, "0")}.jpg`;
}

export function buildProductImageCandidates({ productId, src }) {
  const generatedUrl = buildGeneratedProductImageUrl(productId);
  const resolvedSrc = typeof src === "string" ? src.trim() : "";

  if (generatedUrl && resolvedSrc && generatedUrl !== resolvedSrc) {
    return [generatedUrl, resolvedSrc];
  }
  if (generatedUrl) {
    return [generatedUrl];
  }
  if (resolvedSrc) {
    return [resolvedSrc];
  }
  return [];
}
