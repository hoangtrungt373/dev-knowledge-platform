import { useEffect, useState } from 'react';
import { Box } from '@mui/material';
import ImageNotSupportedIcon from '@mui/icons-material/ImageNotSupported';

interface ThumbnailProps {
  imageUrl?: string | null;
  alt: string;
  width?: number | string;
  height?: number | string;
  borderRadius?: number;
  fallbackIconSize?: number;
  /** Fades the image in on `onLoad` rather than popping in abruptly once decoded — worth the
   * extra state only where the image URL is a freshly re-signed presigned URL on every fetch (so
   * the browser can never serve it from cache) and the element tends to remount often (e.g.
   * `CartPage`'s variant-switch remount, keyed by `variantId`). Off by default. */
  fade?: boolean;
}

/**
 * The "show an image, or a centered fallback icon on a muted background if there isn't one"
 * pattern — extracted once an audit found it implemented four separate times across this feature
 * (`CartPage.tsx`'s own `CartLineThumbnail`, `OrderLineRow.tsx`, `ProductCard.tsx`, each with
 * slightly different sizing/fallback-icon-size/fade behavior, but the same underlying shape).
 * `ProductDetailPage.tsx`'s small gallery thumbnail strip is deliberately **not** folded in here
 * despite visually resembling this at a glance — it's a different concern (an always-real image,
 * click-to-select-active-image with a border highlight, no fallback state at all), not a
 * duplicate of this component's actual job.
 */
export default function Thumbnail({
  imageUrl,
  alt,
  width = 64,
  height = 64,
  borderRadius = 1,
  fallbackIconSize = 28,
  fade = false,
}: ThumbnailProps): JSX.Element {
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    setLoaded(false);
  }, [imageUrl]);

  return (
    <Box
      sx={{
        width,
        height,
        flexShrink: 0,
        bgcolor: 'action.hover',
        borderRadius,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        overflow: 'hidden',
      }}
    >
      {imageUrl ? (
        <Box
          component="img"
          src={imageUrl}
          alt={alt}
          onLoad={fade ? () => setLoaded(true) : undefined}
          sx={{
            width: '100%',
            height: '100%',
            objectFit: 'cover',
            ...(fade ? { opacity: loaded ? 1 : 0, transition: 'opacity 200ms ease-in' } : {}),
          }}
        />
      ) : (
        <ImageNotSupportedIcon sx={{ fontSize: fallbackIconSize, color: 'text.disabled' }} />
      )}
    </Box>
  );
}
