import { useState } from 'react';
import {
  Box,
  Button,
  Chip,
  IconButton,
  Paper,
  Stack,
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
import { ProductVariantInput } from '../types';
import ProductVariantDialog from './ProductVariantDialog';

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
  onRemove: (id: number | string) => void;
  /** True while an add/remove is in flight against the backend (edit mode only — a draft-mode add is synchronous local state). */
  busy?: boolean;
}

// A product can never end up with zero variants (US-1.6) — the backend rejects removing the
// last one, so the remove action is disabled client-side too rather than round-tripping to find
// that out.
export default function ProductVariantEditor({ variants, onAdd, onRemove, busy = false }: Props): JSX.Element {
  const [dialogOpen, setDialogOpen] = useState(false);
  const requiredAttributeKeys = variants.length > 0 ? Object.keys(variants[0].attributes) : [];

  const handleAdd = (input: ProductVariantInput) => {
    onAdd(input);
    setDialogOpen(false);
  };

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1.5 }}>
        <Typography variant="subtitle2" fontWeight={700}>Variants</Typography>
        <Button size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)} disabled={busy}>
          Add Variant
        </Button>
      </Stack>

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
              <TableCell align="right">Remove</TableCell>
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
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                    {Object.entries(variant.attributes).map(([key, value]) => (
                      <Chip key={key} label={`${key}: ${value}`} size="small" variant="outlined" />
                    ))}
                  </Box>
                </TableCell>
                <TableCell align="right">
                  <Tooltip title={variants.length <= 1 ? "Can't remove a product's last variant" : 'Remove'}>
                    <span>
                      <IconButton
                        size="small"
                        onClick={() => onRemove(variant.id)}
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
        requiredAttributeKeys={requiredAttributeKeys}
        saving={busy}
        onClose={() => setDialogOpen(false)}
        onAdd={handleAdd}
      />
    </Paper>
  );
}
