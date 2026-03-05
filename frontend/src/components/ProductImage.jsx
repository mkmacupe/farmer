import { memo, useEffect, useState } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { alpha } from "@mui/material/styles";

const colorCache = new Map();

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
  const [bgColor, setBgColor] = useState(() => src && colorCache.has(src) ? colorCache.get(src) : "#ffffff");

  useEffect(() => {
    setFailed(false);
  }, [src]);

  useEffect(() => {
    if (!src) {
      setBgColor("#ffffff");
      return undefined;
    }
    
    if (colorCache.has(src)) {
      setBgColor(colorCache.get(src));
      return undefined;
    }

    let cancelled = false;
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.src = src;
    img.onload = () => {
      if (cancelled) return;
      
      // Use requestIdleCallback or setTimeout to avoid blocking main thread
      const calcColor = () => {
        if (cancelled) return;
        try {
          const size = 32;
          const canvas = document.createElement("canvas");
          canvas.width = size;
          canvas.height = size;
          const ctx = canvas.getContext("2d", { willReadFrequently: true });
          if (!ctx) return;
          ctx.drawImage(img, 0, 0, size, size);
          const { data } = ctx.getImageData(0, 0, size, size);
          let r = 0;
          let g = 0;
          let b = 0;
          let count = 0;
          const last = size - 1;
          for (let y = 0; y < size; y += 1) {
            for (let x = 0; x < size; x += 1) {
              if (x !== 0 && x !== last && y !== 0 && y !== last) continue;
              const idx = (y * size + x) * 4;
              const alphaValue = data[idx + 3];
              if (alphaValue < 8) continue;
              r += data[idx];
              g += data[idx + 1];
              b += data[idx + 2];
              count += 1;
            }
          }
          let computedColor = "#ffffff";
          if (count > 0) {
            computedColor = `rgb(${Math.round(r / count)}, ${Math.round(g / count)}, ${Math.round(b / count)})`;
          }
          colorCache.set(src, computedColor);
          setBgColor(computedColor);
        } catch {
          setBgColor("#ffffff");
        }
      };

      if ('requestIdleCallback' in window) {
        window.requestIdleCallback(calcColor);
      } else {
        setTimeout(calcColor, 0);
      }
    };
    img.onerror = () => {
      if (!cancelled) {
        setBgColor("#ffffff");
      }
    };
    return () => {
      cancelled = true;
    };
  }, [src]);

  return (
    <Box
      sx={{
        width,
        height,
        borderRadius,
        overflow: "hidden",
        p: hasImage ? 0 : 1.25,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        textAlign: "center",
        backgroundColor: (theme) =>
          hasImage ? bgColor : alpha(theme.palette.primary.main, 0.08),
        color: "text.primary",
        border: (theme) =>
          `1px solid ${alpha(theme.palette.primary.main, 0.28)}`,
      }}
    >
      {hasImage ? (
        <Box
          component="img"
          src={src}
          alt={productName}
          loading="lazy"
          width={typeof width === "number" ? width : undefined}
          height={typeof height === "number" ? height : undefined}
          onError={() => setFailed(true)}
          sx={{
            width: "100%",
            height: "100%",
            display: "block",
            objectFit: "contain",
            backgroundColor: bgColor,
            borderRadius: "inherit",
          }}
        />
      ) : (
        <Typography
          variant="subtitle2"
          component="span"
          sx={{
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
        </Typography>
      )}
    </Box>
  );
}

export default memo(ProductImage);
