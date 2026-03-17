import { describe, expect, it } from "vitest";

import {
  reconcileRequestedItems,
  summarizeProductNames,
} from "./orderCatalogSync.js";

describe("reconcileRequestedItems", () => {
  it("keeps clean items unchanged when catalog is already актуален", () => {
    const catalogProducts = [
      {
        id: 400,
        name: "Сметана 400 г",
        photoUrl: "/images/products/sour-cream.webp",
        stockQuantity: 4,
      },
    ];

    const result = reconcileRequestedItems(
      [
        {
          productId: 400,
          productName: "Сметана 400 г",
          photoUrl: "/images/products/sour-cream.webp",
          quantity: 2,
        },
      ],
      catalogProducts,
    );

    expect(result.items).toEqual([
      {
        product: catalogProducts[0],
        quantity: 2,
      },
    ]);
    expect(result.remapped).toEqual([]);
    expect(result.unavailable).toEqual([]);
    expect(result.quantityAdjusted).toEqual([]);
  });

  it("remaps stale product ids by photoUrl", () => {
    const requestedItems = [
      {
        productId: 11,
        productName: "Молоко 1 л",
        photoUrl: "/images/products/milk.webp",
        quantity: 2,
      },
    ];
    const catalogProducts = [
      {
        id: 501,
        name: "Молоко 1 л",
        photoUrl: "/images/products/milk.webp",
        stockQuantity: 8,
      },
    ];

    const result = reconcileRequestedItems(requestedItems, catalogProducts);

    expect(result.items).toEqual([
      {
        product: catalogProducts[0],
        quantity: 2,
      },
    ]);
    expect(result.remapped).toEqual(["Молоко 1 л"]);
    expect(result.unavailable).toEqual([]);
    expect(result.quantityAdjusted).toEqual([]);
  });

  it("remaps repeat-order items by product name when ids changed", () => {
    const requestedItems = [
      {
        productId: 22,
        productName: "Кефир 1 л",
        quantity: 1,
      },
    ];
    const catalogProducts = [
      {
        id: 602,
        name: "Кефир 1 л",
        photoUrl: "/images/products/kefir.webp",
        stockQuantity: 3,
      },
    ];

    const result = reconcileRequestedItems(requestedItems, catalogProducts);

    expect(result.items[0]).toEqual({
      product: catalogProducts[0],
      quantity: 1,
    });
    expect(result.remapped).toEqual(["Кефир 1 л"]);
  });

  it("marks items as unavailable when they do not exist anymore", () => {
    const result = reconcileRequestedItems(
      [{ productId: 33, productName: "Сыр 300 г", quantity: 1 }],
      [],
    );

    expect(result.items).toEqual([]);
    expect(result.unavailable).toEqual(["Сыр 300 г"]);
  });

  it("tracks quantity adjustments by current stock", () => {
    const catalogProducts = [
      {
        id: 700,
        name: "Яблоки 1 кг",
        photoUrl: "/images/products/apple.webp",
        stockQuantity: 2,
      },
    ];

    const result = reconcileRequestedItems(
      [{ productId: 700, productName: "Яблоки 1 кг", quantity: 5 }],
      catalogProducts,
    );

    expect(result.items).toEqual([
      {
        product: catalogProducts[0],
        quantity: 2,
      },
    ]);
    expect(result.quantityAdjusted).toEqual(["Яблоки 1 кг"]);
  });
});

describe("summarizeProductNames", () => {
  it("summarizes long lists", () => {
    expect(
      summarizeProductNames(["A", "B", "C", "D"], 3),
    ).toBe("A, B, C и ещё 1");
  });
});
