import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Stack,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import ImageNotSupportedIcon from '@mui/icons-material/ImageNotSupported';
import { Product, ProductVariant } from '../../types';
import { shopApi } from '../../api/shopApi';
import VariantSelector from '../../components/shop/VariantSelector';

function formatPriceRange(variants: ProductVariant[]): string {
  const prices = variants.map(v => v.price);
  const min = Math.min(...prices);
  const max = Math.max(...prices);
  return min === max ? `$${min.toFixed(2)}` : `$${min.toFixed(2)} – $${max.toFixed(2)}`;
}

export default function ProductDetailPage(): JSX.Element {
  const { slug } = useParams<{ slug: string }>();
  const navigate = useNavigate();

  const [product, setProduct] = useState<Product | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [activeImageIndex, setActiveImageIndex] = useState(0);
  const [selectedVariant, setSelectedVariant] = useState<ProductVariant | null>(null);

  useEffect(() => {
    if (!slug) return;
    setLoading(true);
    setNotFound(false);
    shopApi.getBySlug(slug).then(setProduct).catch(() => setNotFound(true)).finally(() => setLoading(false));
  }, [slug]);

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '50vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (notFound || !product) {
    return (
      <Box sx={{ p: 3, textAlign: 'center' }}>
        <Typography variant="h6" sx={{ mb: 2 }}>Product not found.</Typography>
        <Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/shop')}>Back to Shop</Button>
      </Box>
    );
  }

  const sortedImages = [...product.images].sort((a, b) => a.sortOrder - b.sortOrder);
  const activeImage = sortedImages[activeImageIndex];
  const priceDisplay = selectedVariant
    ? `$${selectedVariant.price.toFixed(2)}`
    : formatPriceRange(product.variants);
  const inStockDisplay = selectedVariant
    ? selectedVariant.stockQuantity - selectedVariant.reservedQuantity > 0
    : product.variants.some(v => v.stockQuantity - v.reservedQuantity > 0);

  return (
    <Box sx={{ p: 3, maxWidth: 1100, mx: 'auto' }}>
      <Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/shop')} sx={{ mb: 2 }}>
        Back to Shop
      </Button>

      <Box sx={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>

        {/* ── Image gallery ── */}
        <Box sx={{ flex: '1 1 400px', minWidth: 320 }}>
          <Box
            sx={{
              height: 400,
              bgcolor: 'action.hover',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              borderRadius: 1,
              overflow: 'hidden',
              mb: 1.5,
            }}
          >
            {activeImage ? (
              <Box
                component="img"
                src={activeImage.url}
                alt={product.name}
                sx={{ width: '100%', height: '100%', objectFit: 'cover' }}
              />
            ) : (
              <ImageNotSupportedIcon sx={{ fontSize: 64, color: 'text.disabled' }} />
            )}
          </Box>
          {sortedImages.length > 1 && (
            <Stack direction="row" spacing={1}>
              {sortedImages.map((image, index) => (
                <Box
                  key={image.id}
                  component="img"
                  src={image.url}
                  onClick={() => setActiveImageIndex(index)}
                  sx={{
                    width: 64, height: 64, objectFit: 'cover', borderRadius: 1, cursor: 'pointer',
                    border: '2px solid', borderColor: index === activeImageIndex ? 'primary.main' : 'divider',
                  }}
                />
              ))}
            </Stack>
          )}
        </Box>

        {/* ── Info ── */}
        <Box sx={{ flex: '1 1 320px', minWidth: 280 }}>
          <Chip label={product.categoryName} size="small" variant="outlined" sx={{ mb: 1.5 }} />
          <Typography variant="h5" fontWeight={700} sx={{ mb: 1 }}>{product.name}</Typography>
          <Stack direction="row" alignItems="center" spacing={1.5} sx={{ mb: 2 }}>
            <Typography variant="h6" fontWeight={700}>{priceDisplay}</Typography>
            <Chip
              label={inStockDisplay ? 'In stock' : 'Out of stock'}
              size="small"
              color={inStockDisplay ? 'success' : 'default'}
              variant="outlined"
            />
          </Stack>

          {product.description && (
            <Typography variant="body2" color="text.secondary" sx={{ mb: 3, whiteSpace: 'pre-wrap' }}>
              {product.description}
            </Typography>
          )}

          <Divider sx={{ mb: 3 }} />

          <VariantSelector variants={product.variants} onSelect={setSelectedVariant} />
        </Box>
      </Box>
    </Box>
  );
}
