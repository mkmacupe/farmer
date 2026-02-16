const LATIN_PATTERN = /[A-Za-z]/;

function containsLatin(value) {
  return typeof value === 'string' && LATIN_PATTERN.test(value);
}

export function isLegacyEnglishProduct(product) {
  if (!product || typeof product !== 'object') {
    return false;
  }
  return containsLatin(product.name) || containsLatin(product.category);
}

export function filterLocalizedProducts(products) {
  if (!Array.isArray(products)) {
    return [];
  }
  return products.filter((product) => !isLegacyEnglishProduct(product));
}

export function filterLocalizedCategories(categories) {
  if (!Array.isArray(categories)) {
    return [];
  }
  return categories.filter((category) => !containsLatin(category));
}
