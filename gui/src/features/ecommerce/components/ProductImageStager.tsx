import { ChangeEvent, useRef } from 'react';
import {
  Box,
  Button,
  IconButton,
  Paper,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import UploadIcon from '@mui/icons-material/Upload';
import DeleteIcon from '@mui/icons-material/Delete';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';

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

      {images.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          No images yet — added here, uploaded once you create the product.
        </Typography>
      ) : (
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
          {images.map((image, index) => (
            <Box key={image.id} sx={{ width: 140 }}>
              <Box
                component="img"
                src={image.previewUrl}
                alt={`Queued product image, position ${index}`}
                sx={{
                  width: 140, height: 140, objectFit: 'cover', borderRadius: 1,
                  border: '1px solid', borderColor: 'divider',
                }}
              />
              <Stack direction="row" justifyContent="center" spacing={0.5} sx={{ mt: 0.5 }}>
                <Tooltip title="Move earlier">
                  <span>
                    <IconButton size="small" disabled={index === 0} onClick={() => onReorder(image.id, -1)}>
                      <ArrowUpwardIcon fontSize="small" />
                    </IconButton>
                  </span>
                </Tooltip>
                <Tooltip title="Move later">
                  <span>
                    <IconButton
                      size="small"
                      disabled={index === images.length - 1}
                      onClick={() => onReorder(image.id, 1)}
                    >
                      <ArrowDownwardIcon fontSize="small" />
                    </IconButton>
                  </span>
                </Tooltip>
                <Tooltip title="Remove">
                  <span>
                    <IconButton size="small" color="error" onClick={() => onRemove(image.id)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </span>
                </Tooltip>
              </Stack>
            </Box>
          ))}
        </Box>
      )}
    </Paper>
  );
}
