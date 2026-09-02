import { useEffect, useState } from 'react';
import {
  Autocomplete,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Stack,
  TextField,
  Tooltip,
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
   * product must share the same key set (US-1.6), still enforced server-side
   * (`ProductServiceImpl.validateAttributeKeysMatchExisting`). Empty for a brand-new product's
   * first variant, or when editing the product's only variant (nothing else to match). **Advisory
   * only here, same as the category schema above** — this dialog no longer disables any field
   * because of it (an earlier version did, which made every row permanently locked whenever a
   * product had 2+ variants, since any sibling variant *always* has the identical key set by this
   * very invariant); it's only used for `handleSubmit`'s fast, friendly pre-submit message before
   * the backend would otherwise reject a genuine mismatch. */
  requiredAttributeKeys: string[];
  /** The selected product category's own attribute schema, if any — see
   * `useCategoryAttributeSuggestions`. Purely a suggestion/quick-fill aid: clicking one of these
   * adds a pre-filled row, and a row whose key matches one offers that attribute's own controlled
   * vocabulary as autocomplete options — but nothing here is required or restricted. An admin can
   * always add, remove, or freely retype any key/value regardless of what the category suggests
   * (`ProductServiceImpl` never validates a variant's attributes against it — see that class's own
   * Javadoc). */
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
  const [error, setError] = useState('');

  const isEdit = editingVariant !== null;

  useEffect(() => {
    if (!open) return;
    setSku(editingVariant?.sku ?? '');
    setPrice(editingVariant ? String(editingVariant.price) : '');
    setStockQuantity(editingVariant ? String(editingVariant.stockQuantity) : '');
    setError('');
    setAttributes(buildInitialAttributes(requiredAttributeKeys, editingVariant));
    // Deliberately keyed only on [open, editingVariant] — requiredAttributeKeys is read at the
    // moment the dialog opens, same "reset only on open" convention ProductCategoryFormDialog/
    // ProductAttributeFormDialog already use, so a parent re-render with a referentially-new (but
    // logically unchanged) array doesn't reset fields mid-edit.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, editingVariant]);

  const handleClose = () => onClose();

  const updateAttribute = (index: number, field: 'key' | 'value', value: string) => {
    setAttributes(prev => prev.map((a, i) => (i === index ? { ...a, [field]: value } : a)));
  };

  const addAttributeRow = () => setAttributes(prev => [...prev, { key: '', value: '' }]);
  const removeAttributeRow = (index: number) => setAttributes(prev => prev.filter((_, i) => i !== index));

  // Quick-add from a suggested-attribute chip — inserts a pre-filled (empty-value) row for that
  // key, reusing a single already-blank row if one's sitting there rather than piling up empties.
  // A no-op if that key is already present (clicking twice shouldn't duplicate the row).
  const addSuggestedAttributeRow = (attr: SuggestedAttribute) => {
    setAttributes(prev => {
      if (prev.some(a => a.key === attr.name)) return prev;
      const blankIndex = prev.findIndex(a => !a.key.trim() && !a.value.trim());
      const newRow: AttributeRow = { key: attr.name, value: '' };
      if (blankIndex !== -1) {
        const next = [...prev];
        next[blankIndex] = newRow;
        return next;
      }
      return [...prev, newRow];
    });
  };

  const handleSubmit = () => {
    if (!sku.trim()) { setError('SKU is required'); return; }
    const priceNum = Number(price);
    if (!price || Number.isNaN(priceNum) || priceNum < 0) { setError('Price must be a non-negative number'); return; }
    const stockNum = Number(stockQuantity);
    if (!stockQuantity || !Number.isInteger(stockNum) || stockNum < 0) {
      setError('Stock quantity must be a non-negative whole number');
      return;
    }

    const filledAttributes = attributes.filter(a => a.key.trim());
    if (requiredAttributeKeys.length > 0) {
      const providedKeys = new Set(filledAttributes.map(a => a.key.trim()));
      const missing = requiredAttributeKeys.filter(k => !providedKeys.has(k));
      if (missing.length > 0) {
        setError(`This product's other variants use attribute key(s): ${requiredAttributeKeys.join(', ')} — every variant must set the same keys`);
        return;
      }
    }

    setError('');
    const attributeMap: Record<string, string> = {};
    filledAttributes.forEach(a => { attributeMap[a.key.trim()] = a.value.trim(); });
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

          {suggestedAttributes.length > 0 && (
            <Box>
              <Typography variant="caption" color="text.secondary" fontWeight={600}>
                Suggested by this product's category — click to add, edit freely below
              </Typography>
              <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap sx={{ mt: 0.75 }}>
                {suggestedAttributes.map(attr => {
                  const alreadyAdded = attributes.some(a => a.key === attr.name);
                  return (
                    <Tooltip
                      key={attr.attributeId}
                      title={attr.values.length > 0 ? `Values: ${attr.values.join(', ')}` : 'No suggested values'}
                    >
                      <Chip
                        label={attr.required ? `${attr.name} (usually required)` : attr.name}
                        size="small"
                        variant="outlined"
                        clickable={!alreadyAdded}
                        disabled={alreadyAdded}
                        onClick={() => addSuggestedAttributeRow(attr)}
                      />
                    </Tooltip>
                  );
                })}
              </Stack>
            </Box>
          )}

          <Box>
            <Typography variant="caption" color="text.secondary" fontWeight={600}>
              Attributes (e.g. size, color) — add whatever this variant actually needs
            </Typography>
            <Stack spacing={1} sx={{ mt: 1 }}>
              {attributes.map((attr, index) => {
                // A row whose key matches a suggested attribute offers that attribute's own
                // controlled vocabulary as autocomplete options — freeSolo, so typing any other
                // value is still allowed.
                const matchingSuggestion = suggestedAttributes.find(s => s.name === attr.key);
                return (
                  <Stack key={index} direction="row" spacing={1} alignItems="center">
                    <TextField
                      label="Key" size="small" value={attr.key}
                      onChange={e => updateAttribute(index, 'key', e.target.value)}
                      sx={{ flex: 1 }}
                    />
                    {matchingSuggestion && matchingSuggestion.values.length > 0 ? (
                      <Autocomplete
                        freeSolo
                        size="small"
                        options={matchingSuggestion.values}
                        inputValue={attr.value}
                        onInputChange={(_, newValue) => updateAttribute(index, 'value', newValue)}
                        sx={{ flex: 1 }}
                        renderInput={params => <TextField {...params} label="Value" size="small" />}
                      />
                    ) : (
                      <TextField
                        label="Value" size="small" value={attr.value}
                        onChange={e => updateAttribute(index, 'value', e.target.value)}
                        sx={{ flex: 1 }}
                      />
                    )}
                    <IconButton size="small" onClick={() => removeAttributeRow(index)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Stack>
                );
              })}
              <Button size="small" startIcon={<AddIcon />} onClick={addAttributeRow} sx={{ alignSelf: 'flex-start' }}>
                Add attribute
              </Button>
            </Stack>
          </Box>

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
