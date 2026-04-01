import { describe, expect, it } from "vitest";

import {
  buildGeneratedProductImageAlias,
  buildProductImageCandidates,
} from "./productImageSources.js";

describe("productImageSources", () => {
  it("builds generated product image alias from legacy webp source", () => {
    expect(buildGeneratedProductImageAlias("/images/products/potato.webp")).toBe(
      "/images/products/potato.jpg",
    );
    expect(buildGeneratedProductImageAlias("/images/products/mogilev-product-101.webp")).toBe(
      "/images/products/mogilev-product-101.jpg",
    );
  });

  it("returns empty string for unsupported source formats", () => {
    expect(buildGeneratedProductImageAlias(null)).toBe("");
    expect(buildGeneratedProductImageAlias("/images/products/potato.jpg")).toBe("");
    expect(buildGeneratedProductImageAlias("https://example.com/potato.webp")).toBe("");
  });

  it("prefers generated jpg alias and keeps api photoUrl as fallback", () => {
    expect(
      buildProductImageCandidates({
        productId: 431,
        src: "/images/products/potato.webp",
      }),
    ).toEqual([
      "/images/products/potato.jpg",
      "/images/products/potato.webp",
    ]);
  });

  it("falls back to source only when no legacy webp alias can be derived", () => {
    expect(
      buildProductImageCandidates({
        productId: 431,
        src: "/images/products/custom-upload.png",
      }),
    ).toEqual(["/images/products/custom-upload.png"]);
  });

  it("returns an empty list when there is no source at all", () => {
    expect(buildProductImageCandidates({ productId: 103, src: "" })).toEqual([]);
  });
});
