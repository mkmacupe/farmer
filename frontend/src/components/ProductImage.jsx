import { memo, useEffect, useState } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { alpha } from "@mui/material/styles";

function ProductImage({
  src,
  alt,
  height = 160,
  width = "100%",
  borderRadius = 0,
}) {
  const productName = String(alt || "").trim() || "Без названия";
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
  }, [src]);

  return (
    <Box
      sx={{
        width,
        height,
        borderRadius,
        px: 1.25,
        py: 0.75,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        textAlign: "center",
        backgroundColor: (theme) => alpha(theme.palette.primary.main, 0.08),
        color: "text.primary",
        border: (theme) =>
          `1px solid ${alpha(theme.palette.primary.main, 0.28)}`,
      }}
    >
      {src && !failed ? (
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
            objectFit: "cover",
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
