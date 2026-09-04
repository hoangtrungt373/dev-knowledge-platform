import { useState } from 'react';
import {
  Box,
  Button,
  Chip,
  IconButton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import { ProductVariantInput } from '../types';
import { SuggestedAttribute } from '../hooks/useCategoryAttributeSuggestions';
import ProductVariantDialog from './ProductVariantDialog';
import ConfirmDialog from '@shared/components/ConfirmDialog';
import SectionPanel from './common/SectionPanel';

export interface DisplayVariant {
  /** Server id once persisted (edit mode); a locally-generated key while still a draft (create mode). */
  id: number | string;
  sku: string;
  price: number;
  stockQuantity: number;
  reservedQuantity?: number;
  attributes: Record<string, string>;
}

interface Props {
  variants: DisplayVariant[];
  onAdd: (input: ProductVariantInput) => void;
  /** Full-replace update of an existing variant's fields — mirrors the backend's own
   * `ProductApi.updateVariant`/`ProductCommands.VariantInput` "full replace" contract. */
  onUpdate: (id: number | string, input: ProductVariantInput) => void;
  /** May return a promise (edit mode's real backend call) or plain `void` (create mode's
   * synchronous local-array edit) — awaited either way so the confirm dialog closes only once the
   * removal has actually gone through. */
  onRemove: (id: number | string) => void | Promise<void>;
  /** True while an add/update/remove is in flight against the backend (edit mode only — a draft-mode mutation is synchronous local state). */
  busy?: boolean;
  /** The selected product category's own attribute schema, if any (see
   * `useCategoryAttributeSuggestions`) — passed straight through to `ProductVariantDialog`. */
  suggestedAttributes?: SuggestedAttribute[];
}

// A product can never end up with zero variants (US-1.6) — the backend rejects removing the
// last one, so the remove action is disabled client-side too rather than round-tripping to find
// that out.
export default function ProductVariantEditor({
  variants, onAdd, onUpdate, onRemove, busy = false, suggestedAttributes = [],
}: Props): JSX.Element {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingVariant, setEditingVariant] = useState<DisplayVariant | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<DisplayVariant | null>(null);

  // Every variant of one product must share the same attribute keys (US-1.6, still enforced
  // server-side) — computed against the product's *other* variants, excluding whichever one is
  // currently being edited (if any), mirroring the backend's own
  // ProductServiceImpl.validateAttributeKeysMatchExisting. Passed to ProductVariantDialog as an
  // advisory hint for its fast pre-submit message only — it must never disable a field, since any
  // *other* variant always has this exact key set by the very invariant it's checking, which would
  // otherwise permanently lock every row on any product with 2+ variants.
  const referenceVariant = variants.find(v => v.id !== editingVariant?.id);
  const requiredAttributeKeys = referenceVariant ? Object.keys(referenceVariant.attributes) : [];

  const openAddDialog = () => { setEditingVariant(null); setDialogOpen(true); };
  const openEditDialog = (variant: DisplayVariant) => { setEditingVariant(variant); setDialogOpen(true); };
  const closeDialog = () => { setDialogOpen(false); setEditingVariant(null); };

  const handleSubmit = (input: ProductVariantInput) => {
    if (editingVariant) {
      onUpdate(editingVariant.id, input);
    } else {
      onAdd(input);
    }
    closeDialog();
  };

  const handleConfirmDelete = async () => {
    if (!deleteTarget) return;
    await onRemove(deleteTarget.id);
    setDeleteTarget(null);
  };

  return (
    <SectionPanel
      title="Variants"
      action={(
        <Button size="small" startIcon={<AddIcon />} onClick={openAddDialog} disabled={busy}>
          Add Variant
        </Button>
      )}
    >
      {variants.length === 0 ? (
        <Typography variant="body2" color="text.secondary">No variants yet — add at least one.</Typography>
      ) : (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>SKU</TableCell>
              <TableCell>Price</TableCell>
              <TableCell>Stock</TableCell>
              <TableCell>Attributes</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {variants.map(variant => (
              <TableRow key={variant.id}>
                <TableCell sx={{ fontFamily: 'monospace' }}>{variant.sku}</TableCell>
                <TableCell>${variant.price.toFixed(2)}</TableCell>
                <TableCell>
                  {variant.stockQuantity}
                  {variant.reservedQuantity ? ` (${variant.reservedQuantity} reserved)` : ''}
                </TableCell>
                <TableCell>
                  {/* key: value chips, not utils/format.ts's values-only formatVariantLabel — this
                      is an admin table editing raw attribute data (the key itself is what's being
                      managed here), not a shopper-facing label like CartPage's/OrderLineRow's. */}
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                    {Object.entries(variant.attributes).map(([key, value]) => (
                      <Chip key={key} label={`${key}: ${value}`} size="small" variant="outlined" />
                    ))}
                  </Box>
                </TableCell>
                <TableCell align="right">
                  <Tooltip title="Edit">
                    <span>
                      <IconButton size="small" onClick={() => openEditDialog(variant)} disabled={busy}>
                        <EditIcon fontSize="small" />
                      </IconButton>
                    </span>
                  </Tooltip>
                  <Tooltip title={variants.length <= 1 ? "Can't remove a product's last variant" : 'Remove'}>
                    <span>
                      <IconButton
                        size="small"
                        color="error"
                        onClick={() => setDeleteTarget(variant)}
                        disabled={variants.length <= 1 || busy}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </span>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <ProductVariantDialog
        open={dialogOpen}
        editingVariant={editingVariant}
        requiredAttributeKeys={requiredAttributeKeys}
        suggestedAttributes={suggestedAttributes}
        saving={busy}
        onClose={closeDialog}
        onSubmit={handleSubmit}
      />

      <ConfirmDialog
        open={deleteTarget !== null}
        title="Delete Variant"
        message={`Delete variant "${deleteTarget?.sku}"? This cannot be undone.`}
        loading={busy}
        onConfirm={handleConfirmDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </SectionPanel>
  );
}
