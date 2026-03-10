const LATIN_PATTERN = /[A-Za-z]/;
const RU_LOCALE = "ru";

function containsLatin(value) {
  return typeof value === 'string' && LATIN_PATTERN.test(value);
}

function compareLocalizedText(left, right) {
  return String(left || "").localeCompare(String(right || ""), RU_LOCALE, {
    numeric: true,
    sensitivity: "base",
  });
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
  return [...products]
    .filter((product) => !isLegacyEnglishProduct(product))
    .sort((left, right) => {
      const categoryComparison = compareLocalizedText(left?.category, right?.category);
      if (categoryComparison !== 0) {
        return categoryComparison;
      }

      const nameComparison = compareLocalizedText(left?.name, right?.name);
      if (nameComparison !== 0) {
        return nameComparison;
      }

      return Number(left?.id || 0) - Number(right?.id || 0);
    });
}

export function filterLocalizedCategories(categories) {
  if (!Array.isArray(categories)) {
    return [];
  }
  return [...categories]
    .filter((category) => !containsLatin(category))
    .sort(compareLocalizedText);
}
