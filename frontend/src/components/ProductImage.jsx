import { memo, useEffect, useState } from "react";

function ProductImage({
  src,
  alt,
  height = 160,
  width = "100%",
  borderRadius = 0,
}) {
  const productName = String(alt || "").trim() || "Без названия";
  const [failed, setFailed] = useState(false);
  const hasImage = Boolean(src) && !failed;

  useEffect(() => {
    setFailed(false);
  }, [src]);

  const resolvedWidth = typeof width === "number" ? `${width}px` : width;
  const resolvedHeight = typeof height === "number" ? `${height}px` : height;
  const resolvedBorderRadius =
    typeof borderRadius === "number" ? `${borderRadius * 8}px` : borderRadius;

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
        border: "1px solid #d1d5db",
      }}
    >
      {hasImage ? (
        <img
          src={src}
          alt={productName}
          loading="lazy"
          decoding="async"
          width={typeof width === "number" ? width : undefined}
          height={typeof height === "number" ? height : undefined}
          onError={() => setFailed(true)}
          style={{
            width: "100%",
            height: "100%",
            display: "block",
            objectFit: "contain",
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
