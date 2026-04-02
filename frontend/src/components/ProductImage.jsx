import { memo, useEffect, useMemo, useState } from "react";

import { buildProductImageCandidates } from "../utils/productImageSources.js";

function ProductImage({
  productId,
  src,
  alt,
  height = 160,
  width = "100%",
  borderRadius = 0,
  border = "1px solid #d1d5db",
  fit = "contain",
}) {
  const productName = String(alt || "").trim() || "Без названия";
  const candidateSources = useMemo(
    () => buildProductImageCandidates({ productId, src }),
    [productId, src],
  );
  const [activeSourceIndex, setActiveSourceIndex] = useState(0);
  const [failed, setFailed] = useState(false);
  const activeSource = failed ? "" : candidateSources[activeSourceIndex] || "";
  const hasImage = Boolean(activeSource);

  useEffect(() => {
    setActiveSourceIndex(0);
    setFailed(false);
  }, [candidateSources]);

  const numericWidth = typeof width === "number" ? width : undefined;
  const numericHeight = typeof height === "number" ? height : undefined;
  const resolvedWidth = numericWidth == null ? width : `${numericWidth}px`;
  const resolvedHeight = numericHeight == null ? height : `${numericHeight}px`;
  const resolvedBorderRadius =
    typeof borderRadius === "number" ? `${borderRadius * 8}px` : borderRadius;
  const resolvedFit = fit === "cover" ? fit : "contain";

  const handleError = () => {
    if (activeSourceIndex < candidateSources.length - 1) {
      setActiveSourceIndex((current) => current + 1);
      return;
    }
    setFailed(true);
  };

  return (
    <div
      style={{
        width: resolvedWidth,
        height: resolvedHeight,
        borderRadius: resolvedBorderRadius,
        overflow: "hidden",
        padding: hasImage ? 0 : "0.875rem",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        textAlign: "center",
        backgroundColor: hasImage ? "#ffffff" : "#f3f4f6",
        color: "#111827",
        border,
      }}
    >
      {hasImage ? (
        <img
          src={activeSource}
          alt={productName}
          loading="lazy"
          decoding="async"
          width={numericWidth}
          height={numericHeight}
          onError={handleError}
          style={{
            width: "100%",
            height: "100%",
            display: "block",
            objectFit: resolvedFit,
            objectPosition: "center",
            backgroundColor: "#ffffff",
            borderRadius: "inherit",
          }}
        />
      ) : (
        <span
          style={{
            fontSize: "0.875rem",
            fontWeight: 700,
            lineHeight: 1.25,
            display: "-webkit-box",
            overflow: "hidden",
            textOverflow: "ellipsis",
            WebkitBoxOrient: "vertical",
            WebkitLineClamp: 3,
          }}
        >
          {productName}
        </span>
      )}
    </div>
  );
}

export default memo(ProductImage);
