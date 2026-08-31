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
import { Product, ProductCategoryTreeNode, ProductVariantInput } from '../types';
import { ecommerceApi } from '../api/ecommerceApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import ProductVariantEditor, { DisplayVariant } from '../components/ProductVariantEditor';
import ProductImageGallery from '../components/ProductImageGallery';
import ProductImageStager, { StagedImage } from '../components/ProductImageStager';
import ProductDescriptionEditor from '../components/ProductDescriptionEditor';
import { hasVisibleHtmlContent } from '../utils/htmlContent';
import { flattenCategoryTree } from '../utils/categoryTree';

export default function ProductFormPage(): JSX.Element {
  const { id } = useParams<{ id: string }>();
  const isEdit = id !== undefined;
  const navigate = useNavigate();
  const { showError, showSuccess } = useNotification();

  // Fetched as a tree (not the flat list) specifically so the Category picker below can indent
  // each option by depth — same shape ProductCategoryFormDialog's own parent-picker already uses,
  // via the shared flattenCategoryTree util. No excludeIds concern here (unlike that dialog's
  // "can't be its own descendant's parent" rule) — picking a product's category has no cycle risk.
  const [categoryTree, setCategoryTree] = useState<ProductCategoryTreeNode[]>([]);
  const categoryOptions = flattenCategoryTree(categoryTree);

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

  // Create-mode-only: images queued locally (no network call yet — see ProductImageStager's own
  // Javadoc for why this can't just reuse ProductImageGallery). Uploaded one at a time, in order,
  // right after createProduct resolves with the new product's id — see handleSubmit below.
  const [stagedImages, setStagedImages] = useState<StagedImage[]>([]);
  const stagedImageIdCounter = useRef(0);
  // Mirrors stagedImages on every render (no effect needed just to keep a ref in sync) so the
  // unmount-cleanup effect below always revokes the *current* set of blob URLs, not whatever was
  // staged at mount time — a plain `[]`-deps cleanup closure would otherwise only ever see the
  // empty array from the very first render.
  const stagedImagesRef = useRef<StagedImage[]>([]);
  stagedImagesRef.current = stagedImages;

  // Revokes any still-queued preview blob URLs if the admin navigates away (Cancel, back button)
  // without ever submitting — handleSubmit's own success path revokes them explicitly instead
  // (see below), since by then they're either uploaded or abandoned either way.
  useEffect(() => {
    return () => {
      stagedImagesRef.current.forEach(img => URL.revokeObjectURL(img.previewUrl));
    };
  }, []);

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);
  const [variantBusy, setVariantBusy] = useState(false);

  useEffect(() => {
    ecommerceApi.getProductCategoryTree(showError).then(setCategoryTree);
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

        // Only knowable once the product exists — uploadImage needs a real productId (see
        // ProductImageStager's Javadoc). Sequential, not Promise.all: keeps sortOrder assignment
        // (just the loop index) deterministic and avoids hammering the backend with N simultaneous
        // multipart uploads. One failed image doesn't abort the rest, or the product creation
        // itself — the product is already real at this point, so this is best-effort, not
        // all-or-nothing; uploadImage's own showError already surfaces each individual failure.
        let uploadedCount = 0;
        for (let i = 0; i < stagedImages.length; i++) {
          try {
            await ecommerceApi.uploadImage(created.id, stagedImages[i].file, i, showError);
            uploadedCount++;
          } catch {
            // showError already called; keep going so one bad file doesn't block the rest
          }
        }
        stagedImages.forEach(img => URL.revokeObjectURL(img.previewUrl));

        if (stagedImages.length === 0) {
          showSuccess('Product created — add images below');
        } else if (uploadedCount === stagedImages.length) {
          showSuccess(`Product created with ${uploadedCount} image${uploadedCount === 1 ? '' : 's'}`);
        } else {
          showSuccess(`Product created — ${uploadedCount} of ${stagedImages.length} images uploaded; you can retry the rest below`);
        }
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

  const handleAddStagedImage = (file: File) => {
    stagedImageIdCounter.current += 1;
    setStagedImages(prev => [...prev, {
      id: `staged-${stagedImageIdCounter.current}`,
      file,
      previewUrl: URL.createObjectURL(file),
    }]);
  };

  const handleRemoveStagedImage = (imageId: string) => {
    setStagedImages(prev => {
      const target = prev.find(img => img.id === imageId);
      if (target) URL.revokeObjectURL(target.previewUrl);
      return prev.filter(img => img.id !== imageId);
    });
  };

  const handleReorderStagedImage = (imageId: string, direction: -1 | 1) => {
    setStagedImages(prev => {
      const index = prev.findIndex(img => img.id === imageId);
      const otherIndex = index + direction;
      if (index === -1 || otherIndex < 0 || otherIndex >= prev.length) return prev;
      const next = [...prev];
      [next[index], next[otherIndex]] = [next[otherIndex], next[index]];
      return next;
    });
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

      {/* Main (Name → Variants → Images → Description, in that order, per request — Variants/
          Images moved up from below the two-column row now that Images no longer needs a real
          productId to be usable (ProductImageStager works from a blank create form same as
          ProductVariantEditor's own draftVariants already did) + Organization sidebar (Category).
          Same calc()-gap-compensation flex technique ProductDetailPage's own two-column layout
          already established (see gui/CLAUDE.md) — the two subtractions below sum to exactly the
          24px `gap: 3` between them, so the columns actually fill 100% instead of always
          wrapping. */}
      <Box sx={{ display: 'flex', gap: 3, flexWrap: 'wrap', alignItems: 'flex-start' }}>
        <Box sx={{ flex: '1 1 calc(68% - 12px)', minWidth: 420 }}>
          <Stack spacing={3}>
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1.5 }}>Basic Info</Typography>
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
            </Paper>

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

            {/* Images — real upload/reorder/remove in edit mode (a real productId exists); queued
                locally in create mode, uploaded in order right after the product is created (see
                handleSubmit) — no more "save first, come back later" round trip. */}
            {isEdit && product ? (
              <ProductImageGallery productId={product.id} images={product.images} onChanged={loadProduct} />
            ) : (
              <ProductImageStager
                images={stagedImages}
                onAdd={handleAddStagedImage}
                onRemove={handleRemoveStagedImage}
                onReorder={handleReorderStagedImage}
              />
            )}

            <ProductDescriptionEditor value={description} onChange={setDescription} />
          </Stack>
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
                {categoryOptions.map(opt => (
                  <MenuItem key={opt.id} value={opt.id}>
                    <span style={{ paddingLeft: opt.depth * 16 }}>{opt.name}</span>
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Paper>
        </Box>
      </Box>
    </Box>
  );
}
