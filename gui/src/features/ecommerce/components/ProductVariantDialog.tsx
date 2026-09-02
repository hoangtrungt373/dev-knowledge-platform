import { useEffect, useState } from 'react';
import {
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import { ProductVariantInput } from '../types';
import { SuggestedAttribute } from '../hooks/useCategoryAttributeSuggestions';
import type { DisplayVariant } from './ProductVariantEditor';

interface Props {
  open: boolean;
  /** Non-null when editing an existing variant — prefills every field and switches the dialog's
   * title/submit label; null when adding a brand-new one. */
  editingVariant: DisplayVariant | null;
  /** Attribute keys already used by this product's other variants — every variant of one
   * product must share the same key set (US-1.6), so a non-empty product locks the key set to
   * whatever its other variants already have. Empty for a brand-new product's first variant, or
   * when editing the product's only variant (nothing else to match). Only consulted in the
   * free-form editor below (suggestedAttributes.length === 0) — see that branch's own comment. */
  requiredAttributeKeys: string[];
  /** The selected product category's own attribute schema, if any — see
   * `useCategoryAttributeSuggestions`. When non-empty, this dialog renders exactly these
   * attributes (locked keys, a value picker restricted to each one's controlled vocabulary)
   * instead of the free-form key/value editor — see that section's own comment for why. */
  suggestedAttributes: SuggestedAttribute[];
  saving?: boolean;
  onClose: () => void;
  onSubmit: (input: ProductVariantInput) => void;
}

interface AttributeRow { key: string; value: string; }

/** One blank row per required key, or a single fully-blank row when there are none yet (this
 * product's first variant, free to name its own keys) — used both to seed the dialog's initial
 * state and to rebuild it on reset, so the two can't drift out of sync. When editing a variant
 * that already has attributes, those are used verbatim instead (whatever keys it already has). */
function buildInitialAttributes(requiredAttributeKeys: string[], editingVariant: DisplayVariant | null): AttributeRow[] {
  if (editingVariant) {
    const entries = Object.entries(editingVariant.attributes);
    if (entries.length > 0) {
      return entries.map(([key, value]) => ({ key, value }));
    }
  }
  return requiredAttributeKeys.length > 0
    ? requiredAttributeKeys.map(key => ({ key, value: '' }))
    : [{ key: '', value: '' }];
}

export default function ProductVariantDialog({
  open, editingVariant, requiredAttributeKeys, suggestedAttributes, saving = false, onClose, onSubmit,
}: Props): JSX.Element {
  const [sku, setSku] = useState('');
  const [price, setPrice] = useState('');
  const [stockQuantity, setStockQuantity] = useState('');
  const [attributes, setAttributes] = useState<AttributeRow[]>(() => buildInitialAttributes(requiredAttributeKeys, editingVariant));
  // Keyed by attributeId, not name — one selected value per category-suggested attribute; only
  // used when suggestedAttributes is non-empty (see the render branch below).
  const [suggestedValues, setSuggestedValues] = useState<Record<number, string>>({});
  const [error, setError] = useState('');

  const isEdit = editingVariant !== null;

  useEffect(() => {
    if (!open) return;
    setSku(editingVariant?.sku ?? '');
    setPrice(editingVariant ? String(editingVariant.price) : '');
    setStockQuantity(editingVariant ? String(editingVariant.stockQuantity) : '');
    setError('');
    setAttributes(buildInitialAttributes(requiredAttributeKeys, editingVariant));
    const initialSuggested: Record<number, string> = {};
    suggestedAttributes.forEach(attr => {
      initialSuggested[attr.attributeId] = editingVariant?.attributes[attr.name] ?? '';
    });
    setSuggestedValues(initialSuggested);
    // Deliberately keyed only on [open, editingVariant] — requiredAttributeKeys/suggestedAttributes
    // are read at the moment the dialog opens, same "reset only on open" convention
    // ProductCategoryFormDialog/ProductAttributeFormDialog already use, so a parent re-render with
    // a referentially-new (but logically unchanged) array doesn't reset fields mid-edit.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, editingVariant]);

  const handleClose = () => onClose();

  const updateAttribute = (index: number, field: 'key' | 'value', value: string) => {
    setAttributes(prev => prev.map((a, i) => (i === index ? { ...a, [field]: value } : a)));
  };

  const addAttributeRow = () => setAttributes(prev => [...prev, { key: '', value: '' }]);
  const removeAttributeRow = (index: number) => setAttributes(prev => prev.filter((_, i) => i !== index));

  const handleSubmit = () => {
    if (!sku.trim()) { setError('SKU is required'); return; }
    const priceNum = Number(price);
    if (!price || Number.isNaN(priceNum) || priceNum < 0) { setError('Price must be a non-negative number'); return; }
    const stockNum = Number(stockQuantity);
    if (!stockQuantity || !Number.isInteger(stockNum) || stockNum < 0) {
      setError('Stock quantity must be a non-negative whole number');
      return;
    }

    const attributeMap: Record<string, string> = {};
    if (suggestedAttributes.length > 0) {
      const missingRequired = suggestedAttributes.filter(
        attr => attr.required && !suggestedValues[attr.attributeId]?.trim());
      if (missingRequired.length > 0) {
        setError(`This product's category requires: ${missingRequired.map(a => a.name).join(', ')}`);
        return;
      }
      suggestedAttributes.forEach(attr => {
        const value = suggestedValues[attr.attributeId]?.trim();
        if (value) attributeMap[attr.name] = value;
      });
    } else {
      const filledAttributes = attributes.filter(a => a.key.trim());
      if (requiredAttributeKeys.length > 0) {
        const providedKeys = new Set(filledAttributes.map(a => a.key.trim()));
        const missing = requiredAttributeKeys.filter(k => !providedKeys.has(k));
        if (missing.length > 0) {
          setError(`This product's other variants use attribute key(s): ${requiredAttributeKeys.join(', ')} — every variant must set the same keys`);
          return;
        }
      }
      filledAttributes.forEach(a => { attributeMap[a.key.trim()] = a.value.trim(); });
    }

    setError('');
    onSubmit({
      sku: sku.trim(),
      price: priceNum,
      stockQuantity: stockNum,
      attributes: Object.keys(attributeMap).length > 0 ? attributeMap : undefined,
    });
  };

  return (
    <Dialog open={open} onClose={saving ? undefined : handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>{isEdit ? 'Edit Variant' : 'Add Variant'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Stack direction="row" spacing={2}>
            <TextField label="SKU" value={sku} onChange={e => setSku(e.target.value)} fullWidth autoFocus />
            <TextField
              label="Price" type="number" value={price} onChange={e => setPrice(e.target.value)}
              inputProps={{ min: 0, step: '0.01' }} fullWidth
            />
            <TextField
              label="Stock Quantity" type="number" value={stockQuantity}
              onChange={e => setStockQuantity(e.target.value)} inputProps={{ min: 0, step: 1 }} fullWidth
            />
          </Stack>

          {suggestedAttributes.length > 0 ? (
            <Box>
              <Typography variant="caption" color="text.secondary" fontWeight={600}>
                Attributes (from this product's category)
              </Typography>
              <Stack spacing={1.5} sx={{ mt: 1 }}>
                {suggestedAttributes.map(attr => (
                  <FormControl key={attr.attributeId} size="small" fullWidth>
                    <InputLabel>{attr.name}{attr.required ? ' *' : ''}</InputLabel>
                    <Select
                      label={`${attr.name}${attr.required ? ' *' : ''}`}
                      value={suggestedValues[attr.attributeId] ?? ''}
                      onChange={e => setSuggestedValues(prev => ({ ...prev, [attr.attributeId]: e.target.value }))}
                    >
                      {!attr.required && <MenuItem value=""><em>None</em></MenuItem>}
                      {attr.values.map(value => (
                        <MenuItem key={value} value={value}>{value}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                ))}
              </Stack>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                This category defines a fixed attribute schema — an attribute beyond this list would
                be rejected. Manage the schema from Product Categories if it needs to change.
              </Typography>
            </Box>
          ) : (
            <Box>
              <Typography variant="caption" color="text.secondary" fontWeight={600}>
                Attributes (e.g. size, color)
              </Typography>
              <Stack spacing={1} sx={{ mt: 1 }}>
                {attributes.map((attr, index) => {
                  const locked = requiredAttributeKeys.includes(attr.key) && requiredAttributeKeys.length > 0;
                  return (
                    <Stack key={index} direction="row" spacing={1} alignItems="center">
                      <TextField
                        label="Key" size="small" value={attr.key}
                        onChange={e => updateAttribute(index, 'key', e.target.value)}
                        disabled={locked}
                        sx={{ flex: 1 }}
                      />
                      <TextField
                        label="Value" size="small" value={attr.value}
                        onChange={e => updateAttribute(index, 'value', e.target.value)}
                        sx={{ flex: 1 }}
                      />
                      <IconButton size="small" onClick={() => removeAttributeRow(index)} disabled={locked}>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Stack>
                  );
                })}
                {requiredAttributeKeys.length === 0 && (
                  <Button size="small" startIcon={<AddIcon />} onClick={addAttributeRow} sx={{ alignSelf: 'flex-start' }}>
                    Add attribute
                  </Button>
                )}
              </Stack>
            </Box>
          )}

          {error && <Typography variant="body2" color="error">{error}</Typography>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={saving}>Cancel</Button>
        <Button onClick={handleSubmit} variant="contained" disabled={saving}>
          {saving ? <CircularProgress size={16} color="inherit" /> : (isEdit ? 'Save' : 'Add')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
