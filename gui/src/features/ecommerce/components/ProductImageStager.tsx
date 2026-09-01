import { ChangeEvent, useRef } from 'react';
import {
  Button,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import UploadIcon from '@mui/icons-material/Upload';
import ImageThumbnailGrid from './ImageThumbnailGrid';

/**
 * One not-yet-uploaded image queued on the product create form — `id` is a client-generated
 * key (like `ProductVariantEditor`'s `DisplayVariant.id`), never sent to the backend;
 * `previewUrl` is a local `URL.createObjectURL(file)` blob URL, not a server-resolved one.
 */
export interface StagedImage {
  id: string;
  file: File;
  previewUrl: string;
}

interface Props {
  images: StagedImage[];
  onAdd: (file: File) => void;
  onRemove: (id: string) => void;
  onReorder: (id: string, direction: -1 | 1) => void;
}

/**
 * Create-mode counterpart to `ProductImageGallery.tsx` — deliberately a separate component, not
 * a shared/extended one, since every one of that component's handlers fires a real backend call
 * the instant something happens (upload on file pick, `DELETE` on remove, a 3-step scratch-sort-
 * order dance on reorder to route around the `PRODUCT_IMAGE` table's own
 * `UNIQUE(PRODUCT_ID, SORT_ORDER)` constraint). None of that applies here: these files aren't
 * attached to any product yet (there isn't one until the surrounding form's "Create" succeeds),
 * so add/remove/reorder are all synchronous local-array edits — no network call, and no
 * sort-order collision to route around, since nothing is persisted yet. `ProductFormPage`'s
 * `handleSubmit` is what actually uploads these, one `ecommerceApi.uploadImage(newProductId, ...)`
 * call per file, immediately after `createProduct` returns the new id.
 *
 * @author ttg
 */
export default function ProductImageStager({ images, onAdd, onRemove, onReorder }: Props): JSX.Element {
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFileSelected = (e: ChangeEvent<HTMLInputElement>): void => {
    const file = e.target.files?.[0];
    e.target.value = ''; // allow re-selecting the same file next time
    if (!file) return;
    onAdd(file);
  };

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1.5 }}>
        <Typography variant="subtitle2" fontWeight={700}>Image Gallery</Typography>
        <Button size="small" startIcon={<UploadIcon />} onClick={() => fileInputRef.current?.click()}>
          Add Image
        </Button>
        <input ref={fileInputRef} type="file" accept="image/*" hidden onChange={handleFileSelected} />
      </Stack>

      <ImageThumbnailGrid
        items={images.map((image, index) => ({
          id: image.id,
          url: image.previewUrl,
          alt: `Queued product image, position ${index}`,
        }))}
        onMove={onReorder}
        onRemove={onRemove}
        emptyMessage="No images yet — added here, uploaded once you create the product."
      />
    </Paper>
  );
}
