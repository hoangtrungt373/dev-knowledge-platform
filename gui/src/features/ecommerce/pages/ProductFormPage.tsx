import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  FormControl,
  IconButton,
  InputAdornment,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import AddIcon from '@mui/icons-material/Add';
import { Product, ProductCategoryTreeNode, ProductVariantInput } from '../types';
import { ecommerceApi } from '../api/ecommerceApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useSubmitGuard } from '@shared/hooks/useSubmitGuard';
import ProductVariantEditor from '../components/ProductVariantEditor';
import ProductImageGallery from '../components/ProductImageGallery';
import ProductImageStager from '../components/ProductImageStager';
import ProductDescriptionEditor from '../components/ProductDescriptionEditor';
import { hasVisibleHtmlContent } from '../utils/htmlContent';
import { flattenCategoryTree } from '../utils/categoryTree';
import { useProductTags } from '../hooks/useProductTags';
import { useDraftVariants } from '../hooks/useDraftVariants';
import { useStagedImages } from '../hooks/useStagedImages';

/**
 * Create/edit form for one product — the largest, most-iterated page in `@ecommerce` (see
 * `gui/CLAUDE.md`'s own extensive history for this file). Three genuinely separable concerns
 * (the tag picker's own staged-creation flow, the create-mode draft-variant list, and the
 * create-mode staged-image queue) are extracted into their own hooks
 * (`hooks/useProductTags`/`useDraftVariants`/`useStagedImages`) so this component is left owning
 * just: category-tree fetch, the basic fields, edit-mode product loading, and the actual
 * create/update submit — the same "extract a custom hook" remedy `gui/CLAUDE.md` already
 * prescribes for this app's other God Components.
 */
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

  const {
    allTags, selectedTagIds, setSelectedTagIds, stagedTagNames, newTagInput, setNewTagInput,
    toggleTag, handleAddStagedTag, handleRemoveStagedTag, resolveStagedTagIds, clearStagedTagNames,
  } = useProductTags(showError);

  // Create-mode-only: a local, unsaved variant list — a product requires >=1 variant to exist at
  // all (US-1.6), so these travel in the same create request as the basic fields, unlike edit
  // mode where variants are added/removed independently against a real product (handleAddLiveVariant/
  // handleRemoveLiveVariant below stay inline — a couple of lines each, already talking straight
  // to ecommerceApi, nothing worth extracting).
  const { draftVariants, addDraftVariant, removeDraftVariant } = useDraftVariants();

  // Create-mode-only: images queued locally (no network call yet — see ProductImageStager's own
  // Javadoc for why this can't just reuse ProductImageGallery). Uploaded one at a time, in order,
  // right after createProduct resolves with the new product's id — see handleSubmit below.
  const { stagedImages, addStagedImage, removeStagedImage, reorderStagedImage, revokeAll: revokeStagedImages } = useStagedImages();

  // Basic fields
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [productCategoryId, setProductCategoryId] = useState<number | ''>('');

  // Edit-mode-only: the loaded product, re-fetched after any variant/image mutation
  const [product, setProduct] = useState<Product | null>(null);

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(isEdit);
  const { loading: saving, guard } = useSubmitGuard();
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
      setSelectedTagIds(new Set(p.tagIds));
    });
  }, [id, isEdit, showError, setSelectedTagIds]);

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

  const handleSubmit = (): void => {
    if (!validate()) return;
    guard(async () => {
      try {
        const descriptionToSave = hasVisibleHtmlContent(description) ? description : undefined;
        // Every staged (not-yet-real) tag name is created now, right before the product itself is
        // saved — not the moment it was typed. If any creation fails, the whole submit aborts here
        // (via the throw), before the product create/update call ever fires.
        const newTagIds = await resolveStagedTagIds();
        clearStagedTagNames();
        const tagIds = [...selectedTagIds, ...newTagIds];
        if (isEdit && id) {
          await ecommerceApi.updateProduct(Number(id), {
            name: name.trim(),
            description: descriptionToSave,
            productCategoryId: productCategoryId as number,
            tagIds,
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
            tagIds,
          }, showError);

          // Only knowable once the product exists — uploadImage needs a real productId (see
          // ProductImageStager's Javadoc). Sequential, not Promise.all: keeps sortOrder assignment
          // (just the loop index) deterministic and avoids hammering the backend with N
          // simultaneous multipart uploads. One failed image doesn't abort the rest, or the
          // product creation itself — the product is already real at this point, so this is
          // best-effort, not all-or-nothing; uploadImage's own showError already surfaces each
          // individual failure.
          let uploadedCount = 0;
          for (let i = 0; i < stagedImages.length; i++) {
            try {
              await ecommerceApi.uploadImage(created.id, stagedImages[i].file, i, showError);
              uploadedCount++;
            } catch {
              // showError already called; keep going so one bad file doesn't block the rest
            }
          }
          revokeStagedImages();

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
      }
    });
  };

  const handleAddDraftVariant = (input: ProductVariantInput): void => {
    addDraftVariant(input);
    setErrors(prev => ({ ...prev, variants: '' }));
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
                  onRemove={removeDraftVariant}
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
                onAdd={addStagedImage}
                onRemove={removeStagedImage}
                onReorder={reorderStagedImage}
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

          {/* Tags — split into an "Existing tags" Chip-toggle-cloud (mirroring @content's
              QuestionAnswerFormPage picker) and a separate "New tags" section for names typed
              here but not yet created — nothing is persisted to the tag catalog until the product
              itself is saved (see useProductTags' own resolveStagedTagIds). Renaming/deleting an
              already-real tag still requires /admin/product-tags — this section is add-only. */}
          <Paper variant="outlined" sx={{ p: 2, mt: 3 }}>
            <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1.5 }}>
              Tags
            </Typography>

            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>
              Existing tags
            </Typography>
            {allTags.length === 0 ? (
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>No tags yet</Typography>
            ) : (
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75, mb: 2 }}>
                {allTags.map(tag => {
                  const selected = selectedTagIds.has(tag.id);
                  return (
                    <Chip
                      key={tag.id}
                      label={tag.name}
                      size="small"
                      color={selected ? 'primary' : 'default'}
                      variant={selected ? 'filled' : 'outlined'}
                      onClick={() => toggleTag(tag.id)}
                      clickable
                    />
                  );
                })}
              </Box>
            )}

            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>
              New tags
            </Typography>
            <TextField
              placeholder="New tag name…"
              value={newTagInput}
              onChange={e => setNewTagInput(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); handleAddStagedTag(); } }}
              size="small"
              fullWidth
              inputProps={{ maxLength: 100 }}
              InputProps={{
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton
                      size="small"
                      onClick={handleAddStagedTag}
                      disabled={!newTagInput.trim()}
                      title="Queue tag"
                    >
                      <AddIcon fontSize="small" />
                    </IconButton>
                  </InputAdornment>
                ),
              }}
              sx={{ mb: 1 }}
            />
            {stagedTagNames.length === 0 ? (
              <Typography variant="body2" color="text.secondary">No new tags queued</Typography>
            ) : (
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
                {stagedTagNames.map(tagName => (
                  <Chip
                    key={tagName}
                    label={tagName}
                    size="small"
                    color="warning"
                    variant="outlined"
                    onDelete={() => handleRemoveStagedTag(tagName)}
                  />
                ))}
              </Box>
            )}
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
              Created when you {isEdit ? 'save' : 'create'} the product.
            </Typography>
          </Paper>
        </Box>
      </Box>
    </Box>
  );
}
