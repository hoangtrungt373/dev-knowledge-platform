import { useCallback, useEffect, useState } from 'react';
import {
  Box,
  Chip,
  IconButton,
  InputAdornment,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import SearchIcon from '@mui/icons-material/Search';
import { Coupon, CouponTarget } from '../types';
import { ecommerceApi } from '../api/ecommerceApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useSubmitGuard } from '@shared/hooks/useSubmitGuard';
import { useDebouncedValue } from '@shared/hooks/useDebouncedValue';
import CouponFormDialog from '../components/CouponFormDialog';
import Thumbnail from '../components/Thumbnail';
import ConfirmDialog from '@shared/components/ConfirmDialog';
import AdminListHeader from '../components/AdminListHeader';
import TableStatusRow from '../components/TableStatusRow';
import { formatDate, formatPrice, PAGE_SIZE_OPTIONS } from '../utils/format';

const TARGET_LABELS: Record<CouponTarget, string> = {
  SUBTOTAL: 'Subtotal',
  SHIPPING_FEE: 'Shipping Fee',
};

function formatValue(coupon: Coupon): string {
  return coupon.type === 'PERCENTAGE' ? `${coupon.value}%` : formatPrice(coupon.value);
}

/** Admin CRUD for the Coupon ("ProductDiscount") feature, Phase 4 — mirrors `ProductTagListPage`'s
 * shape, with the extra Active/Target filters `CouponApi.list` supports and a richer set of
 * columns (target/value/conditions) `ProductTag` doesn't have. */
export default function CouponListPage(): JSX.Element {
  const { showError, showSuccess } = useNotification();

  const [coupons, setCoupons] = useState<Coupon[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);

  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [searchInput, setSearchInput] = useState('');
  const search = useDebouncedValue(searchInput, 300);
  const [activeFilter, setActiveFilter] = useState<'all' | 'active' | 'inactive'>('all');
  const [targetFilter, setTargetFilter] = useState<'all' | CouponTarget>('all');

  const [formOpen, setFormOpen] = useState(false);
  const [editCoupon, setEditCoupon] = useState<Coupon | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Coupon | null>(null);
  const { loading: deleting, guard: guardDelete } = useSubmitGuard();

  useEffect(() => { setPage(0); }, [search, activeFilter, targetFilter]);

  const fetchCoupons = useCallback(async (opts?: { showSpinner?: boolean }) => {
    const showSpinner = opts?.showSpinner ?? true;
    if (showSpinner) setLoading(true);
    try {
      const data = await ecommerceApi.listCoupons({
        page,
        size: pageSize,
        sortBy: 'id',
        sortDir: 'desc',
        q: search || undefined,
        active: activeFilter === 'all' ? undefined : activeFilter === 'active',
        target: targetFilter === 'all' ? undefined : targetFilter,
      }, showError);
      setCoupons(data.content);
      setTotal(data.totalElements);
    } finally {
      if (showSpinner) setLoading(false);
    }
  }, [page, pageSize, search, activeFilter, targetFilter, showError]);

  useEffect(() => { fetchCoupons(); }, [fetchCoupons]);

  const refreshCoupons = useCallback(() => fetchCoupons({ showSpinner: false }), [fetchCoupons]);

  const openCreate = () => { setEditCoupon(null); setFormOpen(true); };
  const openEdit = (coupon: Coupon) => { setEditCoupon(coupon); setFormOpen(true); };

  const handleDelete = (): void => {
    if (!deleteTarget) return;
    guardDelete(async () => {
      try {
        await ecommerceApi.deleteCoupon(deleteTarget.id, showError);
        showSuccess(`Coupon "${deleteTarget.code}" deleted`);
        setDeleteTarget(null);
        refreshCoupons();
      } catch {
        // showError already called
      }
    });
  };

  return (
    <Box sx={{ p: 3 }}>

      <AdminListHeader
        title="Coupons"
        subtitle={`${total} coupon${total !== 1 ? 's' : ''} total`}
        action={{ label: 'New Coupon', icon: <AddIcon />, onClick: openCreate }}
      />

      {/* Filters */}
      <Stack direction="row" spacing={1.5} sx={{ mb: 2 }}>
        <TextField
          placeholder="Search by code…"
          value={searchInput}
          onChange={e => setSearchInput(e.target.value)}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" color="action" />
              </InputAdornment>
            ),
          }}
          sx={{ width: 240 }}
        />
        <TextField
          select
          label="Status"
          value={activeFilter}
          onChange={e => setActiveFilter(e.target.value as typeof activeFilter)}
          sx={{ width: 160 }}
        >
          <MenuItem value="all">All statuses</MenuItem>
          <MenuItem value="active">Active</MenuItem>
          <MenuItem value="inactive">Inactive</MenuItem>
        </TextField>
        <TextField
          select
          label="Applies to"
          value={targetFilter}
          onChange={e => setTargetFilter(e.target.value as typeof targetFilter)}
          sx={{ width: 180 }}
        >
          <MenuItem value="all">All targets</MenuItem>
          <MenuItem value="SUBTOTAL">Subtotal</MenuItem>
          <MenuItem value="SHIPPING_FEE">Shipping Fee</MenuItem>
        </TextField>
      </Stack>

      {/* Table */}
      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 700 }}>Code</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Applies To</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Value</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Conditions</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Status</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Created</TableCell>
              <TableCell align="right" sx={{ fontWeight: 700 }}>Actions</TableCell>
            </TableRow>
          </TableHead>

          <TableBody>
            {loading || coupons.length === 0 ? (
              <TableStatusRow
                loading={loading}
                isEmpty={coupons.length === 0}
                emptyMessage={search || activeFilter !== 'all' || targetFilter !== 'all'
                  ? 'No coupons match your filters.'
                  : 'No coupons yet. Create the first one.'}
                colSpan={7}
              />
            ) : (
              coupons.map(coupon => (
                <TableRow key={coupon.id} hover>
                  <TableCell>
                    <Stack direction="row" spacing={1.5} alignItems="center">
                      <Thumbnail imageUrl={coupon.imageUrl} alt={coupon.code} width={40} height={40} fallbackIconSize={18} />
                      <Box>
                        <Typography variant="body2" fontWeight={600} sx={{ fontFamily: 'monospace' }}>
                          {coupon.code}
                        </Typography>
                        {coupon.description && (
                          <Typography
                            variant="caption"
                            color="text.secondary"
                            sx={{ display: 'block', maxWidth: 200, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}
                            title={coupon.description}
                          >
                            {coupon.description}
                          </Typography>
                        )}
                      </Box>
                    </Stack>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">{TARGET_LABELS[coupon.target]}</Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">{formatValue(coupon)}</Typography>
                  </TableCell>
                  <TableCell>
                    <Stack spacing={0.25}>
                      {coupon.maxDiscountAmount != null && (
                        <Typography variant="caption" color="text.secondary">
                          Up to {formatPrice(coupon.maxDiscountAmount)} off
                        </Typography>
                      )}
                      {coupon.minSubtotal != null && (
                        <Typography variant="caption" color="text.secondary">
                          Min. subtotal {formatPrice(coupon.minSubtotal)}
                        </Typography>
                      )}
                      {coupon.maxRedemptions != null && (
                        <Typography variant="caption" color="text.secondary">
                          Max {coupon.maxRedemptions} redemptions
                        </Typography>
                      )}
                      {coupon.maxRedemptionsPerUser != null && (
                        <Typography variant="caption" color="text.secondary">
                          Max {coupon.maxRedemptionsPerUser} per user
                        </Typography>
                      )}
                      {(coupon.startAt || coupon.endAt) && (
                        <Typography variant="caption" color="text.secondary">
                          {coupon.startAt ? formatDate(coupon.startAt) : 'Any time'} – {coupon.endAt ? formatDate(coupon.endAt) : 'No end'}
                        </Typography>
                      )}
                      {coupon.maxDiscountAmount == null && coupon.minSubtotal == null && coupon.maxRedemptions == null
                        && coupon.maxRedemptionsPerUser == null && !coupon.startAt && !coupon.endAt && (
                        <Typography variant="caption" color="text.secondary">None</Typography>
                      )}
                    </Stack>
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={coupon.active ? 'Active' : 'Inactive'}
                      size="small"
                      color={coupon.active ? 'success' : 'default'}
                      variant="outlined"
                    />
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary">
                      {formatDate(coupon.createdAt)}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title="Edit">
                      <IconButton size="small" onClick={() => openEdit(coupon)}>
                        <EditIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <IconButton size="small" color="error" onClick={() => setDeleteTarget(coupon)}>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>

        <TablePagination
          component="div"
          count={total}
          page={page}
          rowsPerPage={pageSize}
          rowsPerPageOptions={PAGE_SIZE_OPTIONS}
          onPageChange={(_, p) => setPage(p)}
          onRowsPerPageChange={e => { setPageSize(Number(e.target.value)); setPage(0); }}
        />
      </TableContainer>

      {/* Form dialog */}
      <CouponFormDialog
        open={formOpen}
        coupon={editCoupon}
        onClose={() => setFormOpen(false)}
        onSaved={refreshCoupons}
      />

      {/* Delete confirmation */}
      <ConfirmDialog
        open={deleteTarget !== null}
        title="Delete Coupon"
        message={`Delete "${deleteTarget?.code}"? This cannot be undone. A coupon that has ever been redeemed can't be deleted.`}
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </Box>
  );
}
