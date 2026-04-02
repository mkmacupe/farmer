import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import ProductImage from "./ProductImage.jsx";

describe("ProductImage", () => {
  it("supports custom border styling for card media integration", () => {
    render(
      <ProductImage
        productId={103}
        src="/images/products/test-border.webp"
        alt="Товар без внутренней рамки"
        height={160}
        border="none"
      />,
    );

    expect(
      screen.getByRole("img", { name: "Товар без внутренней рамки" }).parentElement?.style
        .borderStyle,
    ).toBe("none");
  });

  it("uses contain fit by default", () => {
    render(
      <ProductImage
        productId={101}
        src="/images/products/test.webp"
        alt="Тестовый товар"
        height={160}
      />,
    );

    expect(screen.getByRole("img", { name: "Тестовый товар" })).toHaveStyle(
      "object-fit: contain",
    );
  });

  it("supports cover fit for edge-to-edge catalog cards", () => {
    render(
      <ProductImage
        productId={102}
        src="/images/products/test-cover.webp"
        alt="Товар на всю рамку"
        height={160}
        fit="cover"
      />,
    );

    expect(screen.getByRole("img", { name: "Товар на всю рамку" })).toHaveStyle(
      "object-fit: cover",
    );
  });
});
