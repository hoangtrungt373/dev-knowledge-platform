import { useRef, useState } from 'react';
import {
  Button,
  CircularProgress,
} from '@mui/material';
import UploadIcon from '@mui/icons-material/Upload';
import { ProductImage } from '../types';
import { ecommerceApi } from '../api/ecommerceApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import ImageThumbnailGrid from './ImageThumbnailGrid';
import ConfirmDialog from '@shared/components/ConfirmDialog';
import SectionPanel from './common/SectionPanel';

interface Props {
  productId: number;
  images: ProductImage[];
  onChanged: () => void;
}

// A sort-order swap needs a scratch value neither neighbor currently holds, since the backend
// rejects a sort order that collides with any of the product's *other* images — a plain "set A to
// B's value" would collide with B's own still-unmoved row for the instant before B moves too.
// Well outside any real gallery's sort-order range (0..N-1 for a handful of images).
const SCRATCH_SORT_ORDER = 100000;

export default function ProductImageGallery({ productId, images, onChanged }: Props): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [busyImageId, setBusyImageId] = useState<number | null>(null);
  const [deleteTargetId, setDeleteTargetId] = useState<number | null>(null);

  const sorted = [...images].sort((a, b) => a.sortOrder - b.sortOrder);

  const handleFileSelected = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    const nextSortOrder = sorted.length === 0 ? 0 : sorted[sorted.length - 1].sortOrder + 1;
    setUploading(true);
    try {
      await ecommerceApi.uploadImage(productId, file, nextSortOrder, showError);
      showSuccess('Image uploaded');
      onChanged();
    } catch {
      // showError already called
    } finally {
      setUploading(false);
    }
  };

  const handleRemove = async (imageId: number): Promise<boolean> => {
    setBusyImageId(imageId);
    try {
      await ecommerceApi.removeImage(productId, imageId, showError);
      onChanged();
      return true;
    } catch {
      return false; // showError already called
    } finally {
      setBusyImageId(null);
    }
  };

  const handleConfirmDelete = async () => {
    if (deleteTargetId === null) return;
    const success = await handleRemove(deleteTargetId);
    if (success) setDeleteTargetId(null);
  };

  const handleMove = async (imageId: number, direction: -1 | 1) => {
    const index = sorted.findIndex(img => img.id === imageId);
    const current = sorted[index];
    const other = sorted[index + direction];
    if (index === -1 || !other) return;
    setBusyImageId(current.id);
    try {
      await ecommerceApi.updateImageSortOrder(productId, current.id, SCRATCH_SORT_ORDER, showError);
      await ecommerceApi.updateImageSortOrder(productId, other.id, current.sortOrder, showError);
      await ecommerceApi.updateImageSortOrder(productId, current.id, other.sortOrder, showError);
      onChanged();
    } catch {
      // showError already called
    } finally {
      setBusyImageId(null);
    }
  };

  return (
    <SectionPanel
      title="Image Gallery"
      action={(
        <Button
          size="small"
          startIcon={uploading ? <CircularProgress size={14} color="inherit" /> : <UploadIcon />}
          onClick={() => fileInputRef.current?.click()}
          disabled={uploading}
        >
          Upload Image
        </Button>
      )}
    >
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        hidden
        onChange={handleFileSelected}
      />

      <ImageThumbnailGrid
        items={sorted.map(image => ({
          id: image.id,
          url: image.url,
          alt: `Product image, position ${image.sortOrder}`,
        }))}
        busyId={busyImageId}
        onMove={handleMove}
        onRemove={setDeleteTargetId}
        emptyMessage="No images yet."
      />

      <ConfirmDialog
        open={deleteTargetId !== null}
        title="Delete Image"
        message="Delete this image? This cannot be undone."
        loading={busyImageId === deleteTargetId}
        onConfirm={handleConfirmDelete}
        onCancel={() => setDeleteTargetId(null)}
      />
    </SectionPanel>
  );
}
