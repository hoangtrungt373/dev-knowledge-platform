import { MouseEvent as ReactMouseEvent, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Checkbox,
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
import { useNotification } from '@shared/contexts/NotificationContext';
import { useCart } from '../../context/CartContext';
import { shopApi } from '../../api/shopApi';
import VariantSelector from '../../components/shop/VariantSelector';
import Thumbnail from '../../components/Thumbnail';
import { CartLine, ProductVariant } from '../../types';
import { isLowStock, lowStockMessage } from '../../utils/stock';
import { formatPrice, formatVariantLabel } from '../../utils/format';

export default function CartPage(): JSX.Element {
  const navigate = useNavigate();
  const { showError } = useNotification();
  const { cart, loading, refresh, updateItem, removeItem, removeItems, changeVariant } = useCart();
  // Tracks which variantId currently has an in-flight mutation, so only that line's controls
  // disable — a slow updateItem call shouldn't freeze the whole page.
  const [pendingVariantId, setPendingVariantId] = useState<number | null>(null);
  // Multi-select (post-Epic-2 follow-up): which lines are checked, for bulk delete and/or
  // "checkout only these" — starts empty on every visit, not persisted/restored across reloads.
  const [selectedVariantIds, setSelectedVariantIds] = useState<Set<number>>(new Set());
  const [bulkDeleting, setBulkDeleting] = useState(false);

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

  const toggleSelect = (variantId: number): void => {
    setSelectedVariantIds(prev => {
      const next = new Set(prev);
      if (next.has(variantId)) next.delete(variantId); else next.add(variantId);
      return next;
    });
  };

  const toggleSelectAll = (allVariantIds: number[]): void => {
    setSelectedVariantIds(prev => (prev.size === allVariantIds.length ? new Set() : new Set(allVariantIds)));
  };

  const handleDeleteSelected = async (): Promise<void> => {
    if (selectedVariantIds.size === 0) return;
    setBulkDeleting(true);
    try {
      await removeItems([...selectedVariantIds]);
      setSelectedVariantIds(new Set());
    } catch (err) {
      showError(err instanceof Error ? err.message : 'Could not remove the selected items.');
    } finally {
      setBulkDeleting(false);
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
  const allVariantIds = lines.map(l => l.variantId);
  const selectedAvailableVariantIds = lines
    .filter(l => l.available && selectedVariantIds.has(l.variantId))
    .map(l => l.variantId);

  const handleCheckout = (): void => {
    if (selectedAvailableVariantIds.length > 0) {
      navigate('/checkout', { state: { selectedVariantIds: selectedAvailableVariantIds } });
    } else {
      navigate('/checkout');
    }
  };

  return (
    <Box sx={{ p: 3, width: '80%', mx: 'auto' }}>
      <Typography variant="h5" fontWeight={700} sx={{ mb: 3 }}>Your Cart</Typography>

      <Box sx={{ bgcolor: 'background.paper', borderRadius: 2, p: 1, pl:3, mb: 2 }}>
        <Stack direction="row" alignItems="center" justifyContent="space-between">
          <Stack direction="row" alignItems="center" spacing={2}>
            <Checkbox
              checked={selectedVariantIds.size > 0 && selectedVariantIds.size === allVariantIds.length}
              indeterminate={selectedVariantIds.size > 0 && selectedVariantIds.size < allVariantIds.length}
              onChange={() => toggleSelectAll(allVariantIds)}
              disableRipple
              sx={{ p: 0 }}
            />
            <Typography variant="body2" color="text.secondary">
              {selectedVariantIds.size > 0 ? `${selectedVariantIds.size} selected` : 'Select all'}
            </Typography>
          </Stack>
          {/* Always mounted, just hidden when nothing's selected — conditionally rendering this
              button altogether let the row's own height (Checkbox+label alone is shorter than a
              Button) grow the instant a first item got selected. visibility (not display) keeps
              the button out of the tab order and un-clickable while hidden, but still reserves
              its layout box, so the row's height stays constant either way. */}
          <Button
            size="small"
            color="error"
            startIcon={<DeleteOutlineIcon />}
            disabled={bulkDeleting || selectedVariantIds.size === 0}
            onClick={handleDeleteSelected}
            sx={{ visibility: selectedVariantIds.size > 0 ? 'visible' : 'hidden' }}
          >
            {bulkDeleting ? 'Deleting…' : `Delete Selected (${selectedVariantIds.size})`}
          </Button>
        </Stack>
      </Box>

      <Box sx={{ bgcolor: 'background.paper', borderRadius: 2, p: 3, mb: 3 }}>
        <Stack spacing={1} divider={<Divider />} sx={{ mb: 3 }}>
          {lines.map(line => (
            <CartLineRow
              key={line.variantId}
              line={line}
              pending={pendingVariantId === line.variantId}
              selected={selectedVariantIds.has(line.variantId)}
              onToggleSelect={() => toggleSelect(line.variantId)}
              onQuantityChange={(q) => handleQuantityChange(line.variantId, q)}
              onRemove={() => handleRemove(line.variantId)}
              onVariantChange={(variant) => handleVariantChange(line.variantId, variant, line.quantity)}
            />
          ))}
        </Stack>

        <Divider sx={{ mb: 2 }} />

        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Typography variant="body2" color="text.secondary">
            {cart?.itemCount ?? 0} item{cart?.itemCount === 1 ? '' : 's'}
          </Typography>
          <Typography variant="h6" fontWeight={600} color="error.main">
            Subtotal: {formatPrice(cart?.subtotal ?? 0)}
          </Typography>
        </Stack>
      </Box>

      <Stack direction="row" justifyContent="flex-end" spacing={2}>
        <Button onClick={() => navigate('/shop')}>Continue Shopping</Button>
        <Button
          variant="contained"
          size="large"
          disabled={selectedAvailableVariantIds.length === 0 && !hasAvailableLine}
          onClick={handleCheckout}
        >
          {selectedAvailableVariantIds.length > 0
            ? `Checkout Selected (${selectedAvailableVariantIds.length})`
            : 'Proceed to Checkout'}
        </Button>
      </Stack>
    </Box>
  );
}

interface CartLineRowProps {
  line: CartLine;
  pending: boolean;
  selected: boolean;
  onToggleSelect: () => void;
  onQuantityChange: (quantity: number) => void;
  onRemove: () => void;
  onVariantChange: (variant: ProductVariant) => void;
}

function CartLineRow({ line, pending, selected, onToggleSelect, onQuantityChange, onRemove, onVariantChange }: CartLineRowProps): JSX.Element {
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

  const variationLabel = formatVariantLabel(line.attributes);

  if (!line.available) {
    return (
      <Stack
        direction="row"
        alignItems="center"
        spacing={2}
        sx={{ py: 2, opacity: 0.6 }}
      >
        <Checkbox checked={selected} onChange={onToggleSelect} disableRipple sx={{ p: 0 }} />
        <Thumbnail imageUrl={null} alt="" />
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
      sx={{ py: 1 }}
    >
      <Checkbox checked={selected} onChange={onToggleSelect} disableRipple sx={{ p: 0 }} />
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flex: 1, minWidth: 0 }}>
        {/* Only the thumbnail/name are a real link — the variation box and price below are
            plain siblings now, not nested inside it, so clicking them doesn't also navigate. */}
        <Link
          to={`/shop/${line.productSlug}`}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 16,
            minWidth: 0,
            color: 'inherit',
            textDecoration: 'none',
          }}
        >
          <Thumbnail imageUrl={line.primaryImageUrl} alt={line.productName ?? ''} fade />
          <Typography
            variant="body1"
            fontWeight={500}
            noWrap
            sx={{ width: 320, flexShrink: 0, '&:hover': { textDecoration: 'underline' } }}
          >
            {line.productName}
          </Typography>
        </Link>
        <Box sx={{ width: 250, flexShrink: 0 }}>
          {variationLabel && (
            <Box
              onClick={handleVariationBoxClick}
              sx={{
                width: 280,
                boxSizing: 'border-box',
                px: 1,
                py: 0.25,
                borderRadius: 1,
                cursor: 'pointer',
              }}
            >
              <Stack direction="row" alignItems="center" spacing={0.5}>
                <Typography variant="caption" color="text.secondary">Variation:</Typography>
                <KeyboardArrowDownIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
              </Stack>
              <Typography variant="body1">{variationLabel}</Typography>
            </Box>
          )}
        </Box>
        <Typography variant="body2" color="text.primary" sx={{ width: 90, flexShrink: 0, whiteSpace: 'nowrap' }}>
          {formatPrice(line.unitPrice ?? 0)}
        </Typography>
      </Box>

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

      {/* position: relative + the caption below anchored via position: absolute (not spacing/a
          second flow child) — the caption used to always render in flow (just visibility:hidden
          when not low-stock) to keep this column's own height stable across a live quantity edit
          that crosses the low-stock threshold. That reserved second line was itself the bug: since
          every other item in this row (thumbnail, price, delete button) is single-line, that
          reserved line pushed the block's own vertical center below the stepper, so alignItems on
          the outer row centered the whole two-line block instead of the stepper itself — the
          stepper ends up visibly above the row's true center, worse the taller the row's other
          content already is (e.g. the 64px thumbnail). Taking the caption out of flow entirely
          fixes both at once: the column's flow height is now just the stepper's, so it centers
          exactly like every single-line sibling, and there's no layout jump to prevent in the
          first place since an absolutely positioned element never affected flow height either way. */}
      <Stack alignItems="center" sx={{ width: 160, flexShrink: 0, position: 'relative' }}>
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
        {isLowStock(line.availableQuantity) && (
          <Typography
            variant="caption"
            color="warning.main"
            fontWeight={600}
            sx={{
              whiteSpace: 'nowrap',
              position: 'absolute',
              top: '100%',
              left: '50%',
              transform: 'translateX(-50%)',
              mt: 0.5,
            }}
          >
            {lowStockMessage(line.availableQuantity as number)}
          </Typography>
        )}
      </Stack>

      <Typography variant="body1" fontWeight={500} color="error.main" sx={{ minWidth: 80, textAlign: 'right' }}>
        {formatPrice(line.lineTotal ?? 0)}
      </Typography>

      <IconButton onClick={onRemove} disabled={pending} aria-label="Remove">
        <DeleteOutlineIcon />
      </IconButton>
    </Stack>
  );
}
