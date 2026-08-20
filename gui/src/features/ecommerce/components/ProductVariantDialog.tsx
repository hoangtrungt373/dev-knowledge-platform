import { useState } from 'react';
import {
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import { ProductVariantInput } from '../types';

interface Props {
  open: boolean;
  /** Attribute keys already used by this product's other variants — every variant of one
   * product must share the same key set (US-1.6), so a non-empty product locks the key set to
   * whatever its first variant already has. Empty for a brand-new product's first variant. */
  requiredAttributeKeys: string[];
  saving?: boolean;
  onClose: () => void;
  onAdd: (input: ProductVariantInput) => void;
}

interface AttributeRow { key: string; value: string; }

export default function ProductVariantDialog({
  open, requiredAttributeKeys, saving = false, onClose, onAdd,
}: Props): JSX.Element {
  const [sku, setSku] = useState('');
  const [price, setPrice] = useState('');
  const [stockQuantity, setStockQuantity] = useState('');
  const [attributes, setAttributes] = useState<AttributeRow[]>(
    requiredAttributeKeys.length > 0
      ? requiredAttributeKeys.map(key => ({ key, value: '' }))
      : [{ key: '', value: '' }],
  );
  const [error, setError] = useState('');

  const reset = () => {
    setSku(''); setPrice(''); setStockQuantity('');
    setAttributes(requiredAttributeKeys.length > 0
      ? requiredAttributeKeys.map(key => ({ key, value: '' }))
      : [{ key: '', value: '' }]);
    setError('');
  };

  const handleClose = () => { reset(); onClose(); };

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
    onAdd({
      sku: sku.trim(),
      price: priceNum,
      stockQuantity: stockNum,
      attributes: Object.keys(attributeMap).length > 0 ? attributeMap : undefined,
    });
    reset();
  };

  return (
    <Dialog open={open} onClose={saving ? undefined : handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>Add Variant</DialogTitle>
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

          {error && <Typography variant="body2" color="error">{error}</Typography>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={saving}>Cancel</Button>
        <Button onClick={handleSubmit} variant="contained" disabled={saving}>
          {saving ? <CircularProgress size={16} color="inherit" /> : 'Add'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
