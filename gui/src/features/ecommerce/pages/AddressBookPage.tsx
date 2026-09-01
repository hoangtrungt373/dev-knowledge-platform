import { useCallback, useEffect, useState } from 'react';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  IconButton,
  Paper,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import StarIcon from '@mui/icons-material/Star';
import StarOutlineIcon from '@mui/icons-material/StarOutline';
import HomeOutlinedIcon from '@mui/icons-material/HomeOutlined';
import { useNotification } from '@shared/contexts/NotificationContext';
import ConfirmDialog from '@shared/components/ConfirmDialog';
import { addressApi } from '../api/addressApi';
import { SavedAddress } from '../types';
import AddressFormDialog from '../components/AddressFormDialog';

/** The shopper's own "My Addresses" page — never admin-gated, a top-level PrivateRoute like
 * /orders, since every address here belongs to exactly the caller. */
export default function AddressBookPage(): JSX.Element {
  const { showError, showSuccess } = useNotification();

  const [addresses, setAddresses] = useState<SavedAddress[] | null>(null);
  const [loading, setLoading] = useState(true);

  const [formOpen, setFormOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<SavedAddress | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<SavedAddress | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [settingDefaultId, setSettingDefaultId] = useState<number | null>(null);

  const fetchAddresses = useCallback(async (opts?: { showSpinner?: boolean }) => {
    const showSpinner = opts?.showSpinner ?? true;
    if (showSpinner) setLoading(true);
    try {
      setAddresses(await addressApi.list(showError));
    } finally {
      if (showSpinner) setLoading(false);
    }
  }, [showError]);

  useEffect(() => { fetchAddresses(); }, [fetchAddresses]);

  const refresh = useCallback(() => fetchAddresses({ showSpinner: false }), [fetchAddresses]);

  const openCreate = () => { setEditTarget(null); setFormOpen(true); };
  const openEdit = (address: SavedAddress) => { setEditTarget(address); setFormOpen(true); };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await addressApi.remove(deleteTarget.id, showError);
      showSuccess('Address removed');
      setDeleteTarget(null);
      refresh();
    } catch {
      // showError already called
    } finally {
      setDeleting(false);
    }
  };

  const handleSetDefault = async (address: SavedAddress) => {
    setSettingDefaultId(address.id);
    try {
      await addressApi.setDefault(address.id, showError);
      showSuccess('Default address updated');
      refresh();
    } catch {
      // showError already called
    } finally {
      setSettingDefaultId(null);
    }
  };

  if (loading && addresses === null) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '50vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3, width: '80%', mx: 'auto' }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 3 }}>
        <Typography variant="h5" fontWeight={700}>My Addresses</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>
          Add Address
        </Button>
      </Stack>

      {addresses !== null && addresses.length === 0 ? (
        <Box sx={{ textAlign: 'center', mt: 6 }}>
          <HomeOutlinedIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
          <Typography variant="h6" sx={{ mb: 1 }}>No addresses yet</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            Save an address here to reuse it at checkout.
          </Typography>
          <Button variant="contained" onClick={openCreate}>Add Your First Address</Button>
        </Box>
      ) : (
        <Stack spacing={2}>
          {addresses?.map(address => (
            <Paper key={address.id} variant="outlined" sx={{ p: 2.5 }}>
              <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2}>
                <Box sx={{ minWidth: 0 }}>
                  <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 0.5 }}>
                    <Typography variant="subtitle1" fontWeight={600}>
                      {address.label || address.fullName}
                    </Typography>
                    {address.defaultAddress && (
                      <Chip label="Default" size="small" color="primary" variant="outlined" />
                    )}
                  </Stack>
                  <Typography variant="body2" color="text.secondary">{address.fullName}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {address.line1}{address.line2 ? `, ${address.line2}` : ''}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {address.city}, {address.state} {address.postalCode}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">{address.country}</Typography>
                </Box>

                <Stack direction="row" spacing={0.5}>
                  <Tooltip title={address.defaultAddress ? 'Default address' : 'Set as default'}>
                    <span>
                      <IconButton
                        size="small"
                        color={address.defaultAddress ? 'primary' : 'default'}
                        disabled={address.defaultAddress || settingDefaultId === address.id}
                        onClick={() => handleSetDefault(address)}
                      >
                        {address.defaultAddress ? <StarIcon fontSize="small" /> : <StarOutlineIcon fontSize="small" />}
                      </IconButton>
                    </span>
                  </Tooltip>
                  <Tooltip title="Edit">
                    <IconButton size="small" onClick={() => openEdit(address)}>
                      <EditIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Delete">
                    <IconButton size="small" color="error" onClick={() => setDeleteTarget(address)}>
                      <DeleteOutlineIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Stack>
              </Stack>
            </Paper>
          ))}
        </Stack>
      )}

      <AddressFormDialog
        open={formOpen}
        address={editTarget}
        onClose={() => setFormOpen(false)}
        onSaved={refresh}
      />

      <ConfirmDialog
        open={deleteTarget !== null}
        title="Delete Address"
        message={`Delete "${deleteTarget?.label || deleteTarget?.fullName}"? This cannot be undone.`}
        confirmLabel="Delete"
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </Box>
  );
}
