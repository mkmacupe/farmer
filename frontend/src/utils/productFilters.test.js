import { describe, expect, it } from 'vitest';
import {
  filterLocalizedCategories,
  filterLocalizedProducts,
  isLegacyEnglishProduct
} from './productFilters.js';

describe('productFilters', () => {
  it('detects products with latin characters', () => {
    expect(isLegacyEnglishProduct({ name: 'Milk', category: 'Молочная продукция' })).toBe(true);
    expect(isLegacyEnglishProduct({ name: 'Молоко', category: 'Dairy' })).toBe(true);
    expect(isLegacyEnglishProduct({ name: 'Молоко', category: 'Молочная продукция' })).toBe(false);
  });

  it('filters out legacy english products', () => {
    const products = [
      { id: 1, name: 'Milk', category: 'Dairy' },
      { id: 2, name: 'Молоко', category: 'Молочная продукция' }
    ];

    expect(filterLocalizedProducts(products)).toEqual([
      { id: 2, name: 'Молоко', category: 'Молочная продукция' }
    ]);
  });

  it('filters out latin categories', () => {
    const categories = ['Молочная продукция', 'Dairy', 'Овощи'];
    expect(filterLocalizedCategories(categories)).toEqual(['Молочная продукция', 'Овощи']);
  });

  it('handles invalid input gracefully', () => {
    expect(filterLocalizedProducts(null)).toEqual([]);
    expect(filterLocalizedCategories(undefined)).toEqual([]);
  });
});
