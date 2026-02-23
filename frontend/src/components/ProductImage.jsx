import { memo, useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Skeleton from '@mui/material/Skeleton';
import { alpha } from '@mui/material/styles';
import BrokenImageOutlinedIcon from '@mui/icons-material/BrokenImageOutlined';
import ImageOutlinedIcon from '@mui/icons-material/ImageOutlined';

function ProductImage({
  src,
  alt,
  height = 160,
  width = '100%',
  borderRadius = 0,
  priority = false,
  hoverZoom = false
}) {
  const [hasError, setHasError] = useState(false);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    setHasError(false);
    setLoaded(false);
  }, [src]);

  if (!src || hasError) {
    return (
      <Box
        sx={{
          width,
          height,
          borderRadius,
          backgroundColor: (theme) => alpha(theme.palette.text.primary, 0.03),
          color: 'text.disabled',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 0.5,
          fontSize: 11,
          fontWeight: 500,
          border: (theme) => `1px dashed ${alpha(theme.palette.text.primary, 0.12)}`
        }}
      >
        {hasError ? (
          <>
            <BrokenImageOutlinedIcon sx={{ fontSize: 28, opacity: 0.5 }} />
            <span>Фото недоступно</span>
          </>
        ) : (
          <>
            <ImageOutlinedIcon sx={{ fontSize: 28, opacity: 0.4 }} />
            <span>Нет изображения</span>
          </>
        )}
      </Box>
    );
  }

  return (
    <Box
      sx={{
        position: 'relative',
        width,
        height,
        borderRadius,
        overflow: 'hidden',
        bgcolor: (theme) => alpha(theme.palette.text.primary, 0.03),
        '&::after': hoverZoom ? {
          content: '""',
          position: 'absolute',
          inset: 0,
          background: 'linear-gradient(180deg, transparent 65%, rgba(0,0,0,0.03) 100%)',
          pointerEvents: 'none',
          transition: 'opacity 0.3s ease',
          opacity: 0
        } : undefined,
        '&:hover::after': hoverZoom ? {
          opacity: 1
        } : undefined,
        '&:hover img': hoverZoom ? {
          transform: 'scale(1.04)'
        } : undefined
      }}
    >
      {/* Loading skeleton */}
      {!loaded && (
        <Box
          sx={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}
        >
          <Skeleton
            variant="rectangular"
            animation="wave"
            sx={{
              position: 'absolute',
              inset: 0,
              borderRadius: 0
            }}
          />
          <Box
            sx={{
              position: 'relative',
              zIndex: 1,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: 1,
              color: 'text.disabled'
            }}
          >
            <ImageOutlinedIcon
              sx={{
                fontSize: 32,
                opacity: 0.35
              }}
            />
          </Box>
        </Box>
      )}
      
      {/* Actual image */}
      <Box
        component="img"
        src={src}
        alt={alt || 'Фото товара'}
        onLoad={() => setLoaded(true)}
        onError={() => setHasError(true)}
        loading={priority ? 'eager' : 'lazy'}
        decoding="async"
        fetchpriority={priority ? 'high' : 'low'}
        sx={{
          width: '100%',
          height: '100%',
          objectFit: 'cover',
          display: 'block',
          opacity: loaded ? 1 : 0,
          transform: 'scale(1)',
          transition: 'opacity 0.4s ease, transform 0.4s cubic-bezier(0.4, 0, 0.2, 1)'
        }}
      />
    </Box>
  );
}

export default memo(ProductImage);
