import { describe, expect, it } from "vitest";

import {
  buildGeneratedProductImageUrl,
  buildProductImageCandidates,
} from "./productImageSources.js";

describe("productImageSources", () => {
  it("builds generated product image url from numeric id", () => {
    expect(buildGeneratedProductImageUrl(1)).toBe("/images/products/product_0001.jpg");
    expect(buildGeneratedProductImageUrl("25")).toBe("/images/products/product_0025.jpg");
  });

  it("returns empty string for invalid product id", () => {
    expect(buildGeneratedProductImageUrl(null)).toBe("");
    expect(buildGeneratedProductImageUrl(0)).toBe("");
    expect(buildGeneratedProductImageUrl("abc")).toBe("");
  });

  it("prefers generated image and keeps api photoUrl as fallback", () => {
    expect(
      buildProductImageCandidates({
        productId: 7,
        src: "/images/products/milk.webp",
      }),
    ).toEqual([
      "/images/products/product_0007.jpg",
      "/images/products/milk.webp",
    ]);
  });

  it("falls back to source only when generated image is unavailable by id", () => {
    expect(
      buildProductImageCandidates({
        productId: null,
        src: "/images/products/milk.webp",
      }),
    ).toEqual(["/images/products/milk.webp"]);
  });

  it("returns generated url only when source is empty", () => {
    expect(
      buildProductImageCandidates({
        productId: 103,
        src: "",
      }),
    ).toEqual(["/images/products/product_0103.jpg"]);
  });
});
