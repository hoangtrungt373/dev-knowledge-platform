import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Box,
  Button,
  CircularProgress,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { Product, ProductCategory, ProductVariantInput } from '../types';
import { ecommerceApi } from '../api/ecommerceApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import ProductVariantEditor, { DisplayVariant } from '../components/ProductVariantEditor';
import ProductImageGallery from '../components/ProductImageGallery';
import ProductDescriptionEditor from '../components/ProductDescriptionEditor';
import { hasVisibleHtmlContent } from '../utils/htmlContent';

export default function ProductFormPage(): JSX.Element {
  const { id } = useParams<{ id: string }>();
  const isEdit = id !== undefined;
  const navigate = useNavigate();
  const { showError, showSuccess } = useNotification();

  const [categories, setCategories] = useState<ProductCategory[]>([]);

  // Basic fields
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [productCategoryId, setProductCategoryId] = useState<number | ''>('');

  // Edit-mode-only: the loaded product, re-fetched after any variant/image mutation
  const [product, setProduct] = useState<Product | null>(null);

  // Create-mode-only: a local, unsaved variant list — a product requires >=1 variant to exist
  // at all (US-1.6), so these travel in the same create request as the basic fields, unlike
  // edit mode where variants are added/removed independently against a real product.
  const [draftVariants, setDraftVariants] = useState<DisplayVariant[]>([]);
  const draftIdCounter = useRef(0);

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);
  const [variantBusy, setVariantBusy] = useState(false);

  useEffect(() => {
    ecommerceApi.listProductCategories(undefined, showError).then(setCategories);
  }, [showError]);

  const loadProduct = useCallback(() => {
    if (!isEdit || !id) return Promise.resolve();
    return ecommerceApi.getProduct(Number(id), showError).then(p => {
      setProduct(p);
      setName(p.name);
      setDescription(p.description ?? '');
      setProductCategoryId(p.productCategoryId);
    });
  }, [id, isEdit, showError]);

  useEffect(() => {
    if (isEdit) {
      loadProduct().finally(() => setLoading(false));
    }
  }, [isEdit, loadProduct]);

  const validate = (): boolean => {
    const e: Record<string, string> = {};
    if (!name.trim()) e.name = 'Name is required';
    else if (name.trim().length > 150) e.name = 'Name must not exceed 150 characters';
    if (productCategoryId === '') e.productCategoryId = 'Category is required';
    if (!isEdit && draftVariants.length === 0) e.variants = 'At least one variant is required';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSubmit = async () => {
    if (!validate()) return;
    setSaving(true);
    try {
      const descriptionToSave = hasVisibleHtmlContent(description) ? description : undefined;
      if (isEdit && id) {
        await ecommerceApi.updateProduct(Number(id), {
          name: name.trim(),
          description: descriptionToSave,
          productCategoryId: productCategoryId as number,
        }, showError);
        showSuccess('Product updated');
        await loadProduct();
      } else {
        const created = await ecommerceApi.createProduct({
          name: name.trim(),
          description: descriptionToSave,
          productCategoryId: productCategoryId as number,
          variants: draftVariants.map((v): ProductVariantInput => ({
            sku: v.sku, price: v.price, stockQuantity: v.stockQuantity, attributes: v.attributes,
          })),
        }, showError);
        showSuccess('Product created — add images below');
        navigate(`/admin/products/${created.id}/edit`, { replace: true });
      }
    } catch {
      // showError already called
    } finally {
      setSaving(false);
    }
  };

  const handleAddDraftVariant = (input: ProductVariantInput) => {
    draftIdCounter.current += 1;
    setDraftVariants(prev => [...prev, {
      id: `draft-${draftIdCounter.current}`,
      sku: input.sku,
      price: input.price,
      stockQuantity: input.stockQuantity,
      attributes: input.attributes ?? {},
    }]);
    setErrors(prev => ({ ...prev, variants: '' }));
  };

  const handleRemoveDraftVariant = (variantId: number | string) => {
    setDraftVariants(prev => prev.filter(v => v.id !== variantId));
  };

  const handleAddLiveVariant = async (input: ProductVariantInput) => {
    if (!id) return;
    setVariantBusy(true);
    try {
      await ecommerceApi.addVariant(Number(id), input, showError);
      await loadProduct();
    } catch {
      // showError already called
    } finally {
      setVariantBusy(false);
    }
  };

  const handleRemoveLiveVariant = async (variantId: number | string) => {
    if (!id) return;
    setVariantBusy(true);
    try {
      await ecommerceApi.removeVariant(Number(id), Number(variantId), showError);
      await loadProduct();
    } catch {
      // showError already called
    } finally {
      setVariantBusy(false);
    }
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '50vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>

      {/* Header */}
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 3 }}>
        <Stack direction="row" alignItems="center" spacing={1}>
          <IconButton size="small" onClick={() => navigate('/admin/products')} title="Back to list">
            <ArrowBackIcon fontSize="small" />
          </IconButton>
          <Typography variant="h5" fontWeight={700}>
            {isEdit ? 'Edit Product' : 'New Product'}
          </Typography>
        </Stack>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" onClick={() => navigate('/admin/products')} disabled={saving}>
            Cancel
          </Button>
          <Button variant="contained" onClick={handleSubmit} disabled={saving}>
            {saving ? <CircularProgress size={16} color="inherit" /> : isEdit ? 'Save' : 'Create'}
          </Button>
        </Stack>
      </Stack>

      {/* Main (Basic Info, now just Name + Description — wide, no maxWidth cap so the
          description editor actually has room) + Organization sidebar (Category) — per request,
          replacing the old single 900px-capped column both used to share. Same calc()-gap-
          compensation flex technique ProductDetailPage's own two-column layout already
          established (see gui/CLAUDE.md) — the two subtractions below sum to exactly the 24px
          `gap: 3` between them, so the columns actually fill 100% instead of always wrapping. */}
      <Box sx={{ display: 'flex', gap: 3, flexWrap: 'wrap', alignItems: 'flex-start', mb: 3 }}>
        <Box sx={{ flex: '1 1 calc(68% - 12px)', minWidth: 420 }}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1.5 }}>Basic Info</Typography>
            <Stack spacing={2}>
              <TextField
                label="Name"
                value={name}
                onChange={e => { setName(e.target.value); setErrors(p => ({ ...p, name: '' })); }}
                error={!!errors.name}
                helperText={errors.name || 'Slug is auto-generated from name'}
                fullWidth
                autoFocus
                inputProps={{ maxLength: 150 }}
              />
              <ProductDescriptionEditor value={description} onChange={setDescription} />
            </Stack>
          </Paper>
        </Box>

        <Box sx={{ flex: '1 1 calc(32% - 12px)', minWidth: 260 }}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1.5 }}>Organization</Typography>
            <FormControl fullWidth error={!!errors.productCategoryId}>
              <InputLabel>Category</InputLabel>
              <Select
                label="Category"
                value={productCategoryId}
                onChange={e => {
                  setProductCategoryId(e.target.value as number);
                  setErrors(p => ({ ...p, productCategoryId: '' }));
                }}
              >
                {categories.map(c => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
              </Select>
            </FormControl>
          </Paper>
        </Box>
      </Box>

      <Stack spacing={3}>

        {/* Variants */}
        {isEdit && product ? (
          <ProductVariantEditor
            variants={product.variants}
            onAdd={handleAddLiveVariant}
            onRemove={handleRemoveLiveVariant}
            busy={variantBusy}
          />
        ) : (
          <Box>
            <ProductVariantEditor
              variants={draftVariants}
              onAdd={handleAddDraftVariant}
              onRemove={handleRemoveDraftVariant}
            />
            {errors.variants && (
              <Typography variant="body2" color="error" sx={{ mt: 1 }}>{errors.variants}</Typography>
            )}
          </Box>
        )}

        {/* Images — edit mode only, since uploading needs an existing product id */}
        {isEdit && product ? (
          <ProductImageGallery productId={product.id} images={product.images} onChanged={loadProduct} />
        ) : (
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1 }}>Image Gallery</Typography>
            <Typography variant="body2" color="text.secondary">
              Save this product first, then come back here to upload images.
            </Typography>
          </Paper>
        )}

      </Stack>
    </Box>
  );
}
