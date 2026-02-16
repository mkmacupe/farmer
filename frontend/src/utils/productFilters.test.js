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

  it('keeps products without latin letters in both fields', () => {
    const products = [
      { id: 1, name: 'Сметана', category: 'Молочная продукция' },
      { id: 2, name: 'Картофель', category: 'Овощи' }
    ];
    expect(filterLocalizedProducts(products)).toEqual(products);
  });

  it('treats non-object product as non-legacy', () => {
    expect(isLegacyEnglishProduct(null)).toBe(false);
    expect(isLegacyEnglishProduct('milk')).toBe(false);
    expect(isLegacyEnglishProduct(42)).toBe(false);
  });
});
