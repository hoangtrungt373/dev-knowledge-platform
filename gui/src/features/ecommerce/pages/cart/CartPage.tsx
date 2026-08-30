import { MouseEvent as ReactMouseEvent, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  IconButton,
  Popover,
  Stack,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import RemoveIcon from '@mui/icons-material/Remove';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import ShoppingCartOutlinedIcon from '@mui/icons-material/ShoppingCartOutlined';
import ImageNotSupportedIcon from '@mui/icons-material/ImageNotSupported';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useCart } from '../../context/CartContext';
import { shopApi } from '../../api/shopApi';
import VariantSelector from '../../components/shop/VariantSelector';
import { CartLine, ProductVariant } from '../../types';
import { isLowStock, lowStockMessage } from '../../utils/stock';

function formatPrice(value: number): string {
  return `$${value.toFixed(2)}`;
}

export default function CartPage(): JSX.Element {
  const navigate = useNavigate();
  const { showError } = useNotification();
  const { cart, loading, refresh, updateItem, removeItem, changeVariant } = useCart();
  // Tracks which variantId currently has an in-flight mutation, so only that line's controls
  // disable — a slow updateItem call shouldn't freeze the whole page.
  const [pendingVariantId, setPendingVariantId] = useState<number | null>(null);

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleQuantityChange = async (variantId: number, newQuantity: number): Promise<void> => {
    setPendingVariantId(variantId);
    try {
      await updateItem(variantId, newQuantity);
    } catch (err) {
      showError(err instanceof Error ? err.message : 'Could not update this item.');
    } finally {
      setPendingVariantId(null);
    }
  };

  const handleRemove = async (variantId: number): Promise<void> => {
    setPendingVariantId(variantId);
    try {
      await removeItem(variantId);
    } catch (err) {
      showError(err instanceof Error ? err.message : 'Could not remove this item.');
    } finally {
      setPendingVariantId(null);
    }
  };

  // No dedicated backend "swap variant" endpoint (CartApi only has add/update/remove-by-variantId)
  // — composed as add-then-remove via CartContext's changeVariant, which only renders the final
  // state (see that function's own doc comment for why: rendering the add-response on its own
  // would flash a transient extra line for the new variant right before the remove-response makes
  // it disappear again). addItem HINCRBYs, so switching to a variant already elsewhere in the cart
  // merges into that line rather than creating a duplicate one, same as a plain add-to-cart would.
  const handleVariantChange = async (oldVariantId: number, newVariant: ProductVariant, quantity: number): Promise<void> => {
    setPendingVariantId(oldVariantId);
    try {
      await changeVariant(oldVariantId, newVariant.id, quantity);
    } catch (err) {
      showError(err instanceof Error ? err.message : 'Could not switch to that variant.');
    } finally {
      setPendingVariantId(null);
    }
  };

  if (loading && !cart) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '50vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  const lines = cart?.lines ?? [];

  if (lines.length === 0) {
    return (
      <Box sx={{ p: 3, textAlign: 'center', maxWidth: 500, mx: 'auto', mt: 6 }}>
        <ShoppingCartOutlinedIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
        <Typography variant="h6" sx={{ mb: 1 }}>Your cart is empty</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Browse the shop and add something you like.
        </Typography>
        <Button variant="contained" onClick={() => navigate('/shop')}>Go to Shop</Button>
      </Box>
    );
  }

  const hasAvailableLine = lines.some(l => l.available);

  return (
    <Box sx={{ p: 3, maxWidth: 800, mx: 'auto' }}>
      <Typography variant="h5" fontWeight={700} sx={{ mb: 3 }}>Your Cart</Typography>

      <Stack spacing={2} sx={{ mb: 3 }}>
        {lines.map(line => (
          <CartLineRow
            key={line.variantId}
            line={line}
            pending={pendingVariantId === line.variantId}
            onQuantityChange={(q) => handleQuantityChange(line.variantId, q)}
            onRemove={() => handleRemove(line.variantId)}
            onVariantChange={(variant) => handleVariantChange(line.variantId, variant, line.quantity)}
          />
        ))}
      </Stack>

      <Divider sx={{ mb: 2 }} />

      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
        <Typography variant="body2" color="text.secondary">
          {cart?.itemCount ?? 0} item{cart?.itemCount === 1 ? '' : 's'}
        </Typography>
        <Typography variant="h6" fontWeight={700}>
          Subtotal: {formatPrice(cart?.subtotal ?? 0)}
        </Typography>
      </Stack>

      <Stack direction="row" justifyContent="flex-end" spacing={2}>
        <Button onClick={() => navigate('/shop')}>Continue Shopping</Button>
        <Button
          variant="contained"
          size="large"
          disabled={!hasAvailableLine}
          onClick={() => navigate('/checkout')}
        >
          Proceed to Checkout
        </Button>
      </Stack>
    </Box>
  );
}

interface CartLineRowProps {
  line: CartLine;
  pending: boolean;
  onQuantityChange: (quantity: number) => void;
  onRemove: () => void;
  onVariantChange: (variant: ProductVariant) => void;
}

function CartLineRow({ line, pending, onQuantityChange, onRemove, onVariantChange }: CartLineRowProps): JSX.Element {
  const { showError } = useNotification();
  // Fetched lazily on first chip click, not on row mount — most cart lines are never touched, so
  // this avoids one extra shopApi.getBySlug call per line just to render a static row.
  const [variantMenuAnchor, setVariantMenuAnchor] = useState<HTMLElement | null>(null);
  const [productVariants, setProductVariants] = useState<ProductVariant[] | null>(null);
  const [loadingVariants, setLoadingVariants] = useState(false);
  // Whatever VariantSelector currently resolves to inside the open picker — staged, not applied,
  // until Confirm; Cancel (or dismissing the popover any other way) discards it untouched.
  const [pendingVariant, setPendingVariant] = useState<ProductVariant | null>(null);

  const handleVariationBoxClick = async (e: ReactMouseEvent<HTMLElement>): Promise<void> => {
    e.preventDefault(); // don't follow the enclosing product-detail Link
    e.stopPropagation();
    setVariantMenuAnchor(e.currentTarget);
    if (productVariants || !line.productSlug) return;
    setLoadingVariants(true);
    try {
      const product = await shopApi.getBySlug(line.productSlug);
      setProductVariants(product.variants);
    } catch (err) {
      showError(err instanceof Error ? err.message : 'Could not load this product\'s variants.');
      setVariantMenuAnchor(null);
    } finally {
      setLoadingVariants(false);
    }
  };

  const closeVariantMenu = (): void => {
    setVariantMenuAnchor(null);
    setPendingVariant(null);
  };

  const handleConfirmVariant = (): void => {
    if (pendingVariant && pendingVariant.id !== line.variantId) {
      onVariantChange(pendingVariant);
    }
    closeVariantMenu();
  };

  const variationLabel = line.attributes
    ? Object.entries(line.attributes).map(([key, value]) => `${key}: ${value}`).join(', ')
    : '';

  if (!line.available) {
    return (
      <Stack
        direction="row"
        alignItems="center"
        spacing={2}
        sx={{ p: 2, borderRadius: 1, border: '1px solid', borderColor: 'divider', opacity: 0.6 }}
      >
        <CartLineThumbnail imageUrl={null} alt="" />
        <Box sx={{ flex: 1 }}>
          <Typography variant="body2">Variant #{line.variantId}</Typography>
          <Chip label="No longer available" size="small" color="warning" variant="outlined" sx={{ mt: 0.5 }} />
        </Box>
        <IconButton onClick={onRemove} disabled={pending} aria-label="Remove">
          <DeleteOutlineIcon />
        </IconButton>
      </Stack>
    );
  }

  return (
    <Stack
      direction="row"
      alignItems="center"
      spacing={2}
      sx={{ p: 2, borderRadius: 1, border: '1px solid', borderColor: 'divider' }}
    >
      <Link
        to={`/shop/${line.productSlug}`}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 16,
          flex: 1,
          minWidth: 0,
          color: 'inherit',
          textDecoration: 'none',
        }}
      >
        <CartLineThumbnail imageUrl={line.primaryImageUrl} alt={line.productName ?? ''} />
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography variant="body1" fontWeight={600} sx={{ '&:hover': { textDecoration: 'underline' } }}>
            {line.productName}
          </Typography>
          {variationLabel && (
            <Box
              onClick={handleVariationBoxClick}
              sx={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 0.5,
                mt: 0.5,
                px: 1,
                py: 0.25,
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 1,
                cursor: 'pointer',
                '&:hover': { bgcolor: 'action.hover' },
              }}
            >
              <Typography variant="caption" color="text.secondary">Variation:</Typography>
              <Typography variant="caption" fontWeight={600}>{variationLabel}</Typography>
              <KeyboardArrowDownIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
            </Box>
          )}
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            {formatPrice(line.unitPrice ?? 0)} each
          </Typography>
          {isLowStock(line.availableQuantity) && (
            <Typography variant="caption" color="warning.main" fontWeight={600} sx={{ display: 'block', mt: 0.25 }}>
              {lowStockMessage(line.availableQuantity as number)}
            </Typography>
          )}
        </Box>
      </Link>

      <Popover
        open={Boolean(variantMenuAnchor)}
        anchorEl={variantMenuAnchor}
        onClose={closeVariantMenu}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
      >
        <Box sx={{ p: 2, minWidth: 240 }}>
          {loadingVariants ? (
            <CircularProgress size={20} />
          ) : productVariants && productVariants.length > 1 ? (
            <>
              <VariantSelector
                variants={productVariants}
                initialAttributes={line.attributes}
                onSelect={setPendingVariant}
              />
              <Stack direction="row" justifyContent="flex-end" spacing={1} sx={{ mt: 2 }}>
                <Button size="small" onClick={closeVariantMenu}>Cancel</Button>
                <Button
                  size="small"
                  variant="contained"
                  disabled={!pendingVariant || pendingVariant.id === line.variantId}
                  onClick={handleConfirmVariant}
                >
                  Confirm
                </Button>
              </Stack>
            </>
          ) : (
            <Typography variant="body2" color="text.secondary">
              No other variants for this product.
            </Typography>
          )}
        </Box>
      </Popover>

      <Stack direction="row" alignItems="center" sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
        <IconButton
          size="small"
          disabled={pending || line.quantity <= 1}
          onClick={() => onQuantityChange(line.quantity - 1)}
        >
          <RemoveIcon fontSize="small" />
        </IconButton>
        <Typography sx={{ width: 28, textAlign: 'center' }}>{line.quantity}</Typography>
        <IconButton
          size="small"
          disabled={pending || (line.availableQuantity !== undefined && line.quantity >= line.availableQuantity)}
          onClick={() => onQuantityChange(line.quantity + 1)}
        >
          <AddIcon fontSize="small" />
        </IconButton>
      </Stack>

      <Typography variant="body1" fontWeight={700} sx={{ minWidth: 80, textAlign: 'right' }}>
        {formatPrice(line.lineTotal ?? 0)}
      </Typography>

      <IconButton onClick={onRemove} disabled={pending} aria-label="Remove">
        <DeleteOutlineIcon />
      </IconButton>
    </Stack>
  );
}

/**
 * Same fallback-icon-on-no-image treatment as `ProductCard.tsx`'s storefront-grid thumbnail, plus
 * a fade-in on load. `primaryImageUrl` is a time-limited presigned URL, re-signed on every cart
 * fetch — even switching between two variants of the *same* product (same underlying picture)
 * gets a new URL string, so the browser can't serve it from cache. Combined with `CartLineRow`
 * being keyed by `variantId` (a swap necessarily mounts a brand-new `<img>` node, not just a new
 * `src` on an existing one), that means a real network fetch+decode on every switch — this can't
 * be hidden entirely, but fading in on `onLoad` (rather than popping in abruptly once decoded)
 * reads as a smooth transition instead of a blink.
 */
function CartLineThumbnail({ imageUrl, alt }: { imageUrl?: string | null; alt: string }): JSX.Element {
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    setLoaded(false);
  }, [imageUrl]);

  return (
    <Box
      sx={{
        width: 64,
        height: 64,
        flexShrink: 0,
        bgcolor: 'action.hover',
        borderRadius: 1,
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
          onLoad={() => setLoaded(true)}
          sx={{
            width: '100%',
            height: '100%',
            objectFit: 'cover',
            opacity: loaded ? 1 : 0,
            transition: 'opacity 200ms ease-in',
          }}
        />
      ) : (
        <ImageNotSupportedIcon sx={{ fontSize: 28, color: 'text.disabled' }} />
      )}
    </Box>
  );
}
