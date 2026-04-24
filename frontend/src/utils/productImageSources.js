export function buildGeneratedProductImageAlias(src) {
  const resolvedSrc = typeof src === "string" ? src.trim() : "";
  if (!resolvedSrc) {
    return "";
  }
  const match = resolvedSrc.match(/^\/images\/products\/([a-z0-9-]+)\.webp$/i);
  if (!match) {
    return "";
  }
  return `/images/products/${match[1]}.jpg`;
}

export function buildProductImageCandidates({ src }) {
  const generatedUrl = buildGeneratedProductImageAlias(src);
  const resolvedSrc = typeof src === "string" ? src.trim() : "";

  if (generatedUrl && resolvedSrc && generatedUrl !== resolvedSrc) {
    return [resolvedSrc, generatedUrl];
  }
  if (generatedUrl) {
    return [generatedUrl];
  }
  if (resolvedSrc) {
    return [resolvedSrc];
  }
  return [];
}
