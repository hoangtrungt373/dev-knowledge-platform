import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Box,
  Button,
  CircularProgress,
  Divider,
  IconButton,
  Rating,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import ImageNotSupportedIcon from '@mui/icons-material/ImageNotSupported';
import AddIcon from '@mui/icons-material/Add';
import RemoveIcon from '@mui/icons-material/Remove';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import FlagOutlinedIcon from '@mui/icons-material/FlagOutlined';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import { authService } from '@auth/services/authService';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useCart } from '../../context/CartContext';
import { Product, ProductVariant } from '../../types';
import { shopApi } from '../../api/shopApi';
import VariantSelector from '../../components/shop/VariantSelector';
import { isLowStock, lowStockMessage } from '../../utils/stock';

function formatPriceRange(variants: ProductVariant[]): string {
  const prices = variants.map(v => v.price);
  const min = Math.min(...prices);
  const max = Math.max(...prices);
  return min === max ? `$${min.toFixed(2)}` : `$${min.toFixed(2)} – $${max.toFixed(2)}`;
}

// Faked pending a real reviews/order-analytics backend (Epic 5) — same number on every product.
const FAKE_RATING = 4.9;
const FAKE_RATING_COUNT = 79;
const FAKE_SOLD_COUNT = 1000;

export default function ProductDetailPage(): JSX.Element {
  const { slug } = useParams<{ slug: string }>();
  const navigate = useNavigate();
  const { showError, showSuccess } = useNotification();
  const { addItem } = useCart();

  const [product, setProduct] = useState<Product | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [activeImageIndex, setActiveImageIndex] = useState(0);
  const [selectedVariant, setSelectedVariant] = useState<ProductVariant | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [addingToCart, setAddingToCart] = useState(false);

  useEffect(() => {
    if (!slug) return;
    setLoading(true);
    setNotFound(false);
    shopApi.getBySlug(slug).then(setProduct).catch(() => setNotFound(true)).finally(() => setLoading(false));
  }, [slug]);

  // A variant switch invalidates whatever quantity was picked for the previous one — reset rather
  // than silently carrying a stale value into the next add-to-cart call.
  useEffect(() => {
    setQuantity(1);
  }, [selectedVariant?.id]);

  const handleAddToCart = async (): Promise<void> => {
    if (!selectedVariant) return;
    setAddingToCart(true);
    try {
      await addItem(selectedVariant.id, quantity);
      showSuccess(`Added ${quantity} × ${product?.name} to your cart.`);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Could not add this item to your cart.';
      showError(message);
    } finally {
      setAddingToCart(false);
    }
  };

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
  const showSlideArrows = sortedImages.length > 1;
  const slideBy = (delta: number): void => {
    setActiveImageIndex(prev => (prev + delta + sortedImages.length) % sortedImages.length);
  };
  const priceDisplay = selectedVariant
    ? `$${selectedVariant.price.toFixed(2)}`
    : formatPriceRange(product.variants);
  // Only meaningful once a specific variant is picked — different variants have independent
  // stock, so there's no single sensible "remaining" number to show before that (the chip below
  // falls back to a plain any-variant-in-stock check in that case, same as before).
  const availableForSelectedVariant = selectedVariant
    ? selectedVariant.stockQuantity - selectedVariant.reservedQuantity
    : undefined;
  const inStockDisplay = selectedVariant
    ? (availableForSelectedVariant ?? 0) > 0
    : product.variants.some(v => v.stockQuantity - v.reservedQuantity > 0);

  return (
    <Box sx={{ p: 3, width: '80%', mx: 'auto' }}>
      <Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/shop')} sx={{ mb: 2 }}>
        Back to Shop
      </Button>

      <Box sx={{ display: 'flex', gap: 4, flexWrap: 'wrap', bgcolor: 'background.paper', borderRadius: 2, p: 3 }}>

        {/* ── Image gallery ── */}
        <Box sx={{ flex: '1 1 calc(45% - 12.8px)', minWidth: 320 }}>
          <Box
            sx={{
              position: 'relative',
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
            {showSlideArrows && (
              <>
                <IconButton
                  aria-label="Previous image"
                  onClick={() => slideBy(-1)}
                  sx={{
                    position: 'absolute', left: 8, top: '50%', transform: 'translateY(-50%)',
                    bgcolor: 'background.paper', boxShadow: 1,
                    '&:hover': { bgcolor: 'background.paper' },
                  }}
                >
                  <ChevronLeftIcon />
                </IconButton>
                <IconButton
                  aria-label="Next image"
                  onClick={() => slideBy(1)}
                  sx={{
                    position: 'absolute', right: 8, top: '50%', transform: 'translateY(-50%)',
                    bgcolor: 'background.paper', boxShadow: 1,
                    '&:hover': { bgcolor: 'background.paper' },
                  }}
                >
                  <ChevronRightIcon />
                </IconButton>
                <Stack
                  direction="row"
                  spacing={0.75}
                  sx={{ position: 'absolute', bottom: 10, left: '50%', transform: 'translateX(-50%)' }}
                >
                  {sortedImages.map((image, index) => (
                    <Box
                      key={image.id}
                      onClick={() => setActiveImageIndex(index)}
                      sx={{
                        width: 8, height: 8, borderRadius: '50%', cursor: 'pointer',
                        bgcolor: index === activeImageIndex ? 'primary.main' : 'background.paper',
                        boxShadow: 1,
                      }}
                    />
                  ))}
                </Stack>
              </>
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
        <Box sx={{ flex: '1 1 calc(55% - 19.2px)', minWidth: 320 }}>
          <Typography variant="h5" fontWeight={400} sx={{ mb: 2 }}>{product.name}</Typography>

          {/* Rating / sold-count / report row — faked pending a real reviews/order-analytics
              backend (Epic 5); same numbers on every product until that lands. */}
          <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mb: 2, flexWrap: 'wrap' }}>
            <Stack direction="row" alignItems="center" spacing={0.5}>
              <Typography variant="body1" fontWeight={600}>{FAKE_RATING.toFixed(1)}</Typography>
              <Rating value={FAKE_RATING} precision={0.1} readOnly size="small" />
            </Stack>
            <Divider orientation="vertical" flexItem />
            <Typography variant="body1" color="text.secondary">{FAKE_RATING_COUNT} Ratings</Typography>
            <Divider orientation="vertical" flexItem />
            <Typography variant="body1" color="text.secondary">{FAKE_SOLD_COUNT} Sold</Typography>
            <Box sx={{ flexGrow: 1 }} />
            {/* Not implemented yet — disabled rather than silently doing nothing on click. */}
            <Tooltip title="Coming soon">
              <span>
                <Button
                  size="small"
                  color="inherit"
                  disabled
                  startIcon={<FlagOutlinedIcon fontSize="small" />}
                  sx={{ textTransform: 'none' }}
                >
                  Report
                </Button>
              </span>
            </Tooltip>
          </Stack>

          {/* Price — a distinct, higher-emphasis block than the title above it, Shopee-style:
              gray-filled box, red highlight, and a bigger type size than the h5 title. */}
          <Box sx={{ bgcolor: 'action.hover', borderRadius: 1, px: 2, py: 1.25, mb: 3 }}>
            <Typography variant="h4" fontWeight={400} color="error.main">
              {priceDisplay}
            </Typography>
          </Box>

          {/* Category and description move to an "article" section below the info panel in a
              later phase — not shown here anymore. */}

          <Divider sx={{ mb: 3 }} />

          <VariantSelector variants={product.variants} onSelect={setSelectedVariant} layout="row" />

          <Stack spacing={2.5} sx={{ mt: 3 }}>
            <Stack direction="row" spacing={2} alignItems="center">
              <Typography
                variant="body1"
                color="text.secondary"
                fontWeight={600}
                sx={{ minWidth: 72, flexShrink: 0 }}
              >
                Quantity
              </Typography>
              <Stack direction="row" alignItems="center" sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
                <IconButton size="small" onClick={() => setQuantity(q => Math.max(1, q - 1))} disabled={quantity <= 1}>
                  <RemoveIcon fontSize="small" />
                </IconButton>
                <TextField
                  value={quantity}
                  onChange={(e) => {
                    const parsed = Math.max(1, parseInt(e.target.value, 10) || 1);
                    setQuantity(availableForSelectedVariant !== undefined ? Math.min(parsed, availableForSelectedVariant) : parsed);
                  }}
                  variant="standard"
                  InputProps={{ disableUnderline: true }}
                  inputProps={{ style: { textAlign: 'center', width: 32, padding: 0 } }}
                />
                <IconButton
                  size="small"
                  disabled={availableForSelectedVariant !== undefined && quantity >= availableForSelectedVariant}
                  onClick={() => setQuantity(q => (availableForSelectedVariant !== undefined ? Math.min(q + 1, availableForSelectedVariant) : q + 1))}
                >
                  <AddIcon fontSize="small" />
                </IconButton>
              </Stack>

              {/* Before a variant is resolved, only "any variant in stock" is known (per-variant
                  stock is independent) — a plain IN STOCK/Out of stock. Once resolved, the exact
                  count takes over, with the low-stock warning replacing it under the threshold. */}
              {!selectedVariant ? (
                <Typography variant="body1" color={inStockDisplay ? 'text.secondary' : 'text.disabled'} fontWeight={600}>
                  {inStockDisplay ? 'IN STOCK' : 'Out of stock'}
                </Typography>
              ) : isLowStock(availableForSelectedVariant) ? (
                <Typography variant="body1" color="warning.main" fontWeight={600}>
                  {lowStockMessage(availableForSelectedVariant as number)}
                </Typography>
              ) : inStockDisplay ? (
                <Typography variant="body1" color="text.secondary">
                  {availableForSelectedVariant} item{availableForSelectedVariant === 1 ? '' : 's'} available
                </Typography>
              ) : (
                <Typography variant="body1" color="text.disabled">
                  Out of stock
                </Typography>
              )}
            </Stack>

            <Stack direction="row" spacing={2}>
              {authService.isAuthenticated() ? (
                <>
                  <Button
                    variant="contained"
                    size="large"
                    startIcon={<ShoppingCartIcon />}
                    disabled={!selectedVariant || !inStockDisplay || addingToCart}
                    onClick={handleAddToCart}
                    sx={{ px: 4, py: 1.25, fontSize: '1rem', fontWeight: 600 }}
                  >
                    {addingToCart ? 'Adding…' : 'Add to Cart'}
                  </Button>
                  {/* Buy Now: not implemented yet (no "skip cart, go straight to checkout" flow
                      exists) — kept visible per the Shopee-style layout this page follows, but
                      disabled with a tooltip rather than silently omitted, so it doesn't read as
                      a working shortcut that just does nothing. */}
                  <Tooltip title="Coming soon">
                    <span>
                      <Button
                        variant="outlined"
                        size="large"
                        disabled
                        sx={{ px: 4, py: 1.25, fontSize: '1rem', fontWeight: 600 }}
                      >
                        Buy Now
                      </Button>
                    </span>
                  </Tooltip>
                </>
              ) : (
                <Button
                  variant="contained"
                  size="large"
                  onClick={() => navigate('/login')}
                  sx={{ px: 4, py: 1.25, fontSize: '1rem', fontWeight: 600 }}
                >
                  Log in to buy
                </Button>
              )}
            </Stack>
          </Stack>
        </Box>
      </Box>
    </Box>
  );
}
