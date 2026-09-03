import { useCallback, useEffect, useState } from 'react';
import {
  Box,
  Chip,
  IconButton,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import LocalShippingIcon from '@mui/icons-material/LocalShipping';
import AssignmentTurnedInIcon from '@mui/icons-material/AssignmentTurnedIn';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useSubmitGuard } from '@shared/hooks/useSubmitGuard';
import ConfirmDialog from '@shared/components/ConfirmDialog';
import { adminOrderApi } from '../api/adminOrderApi';
import { Order, OrderStatus } from '../types';
import { ORDER_STATUS_COLORS, ORDER_STATUS_LABELS, formatOrderDate } from '../utils/orderStatus';
import { formatPrice, PAGE_SIZE_OPTIONS } from '../utils/format';
import AdminListHeader from '../components/AdminListHeader';
import TableStatusRow from '../components/TableStatusRow';

const ALL_STATUSES: OrderStatus[] = [
  'PENDING', 'PAYMENT_PROCESSING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'FAILED', 'EXPIRED',
];

interface ActionTarget {
  order: Order;
  action: 'ship' | 'deliver';
}

export default function AdminOrderListPage(): JSX.Element {
  const { showError, showSuccess } = useNotification();

  const [orders, setOrders] = useState<Order[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);

  // Defaults to CONFIRMED — "ready to ship" is this queue's primary reason to exist; switch to
  // SHIPPED for "ready to mark delivered", or All Statuses to browse everything.
  const [statusFilter, setStatusFilter] = useState<OrderStatus | ''>('CONFIRMED');
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);

  const [actionTarget, setActionTarget] = useState<ActionTarget | null>(null);
  const { loading: actionLoading, guard: guardAction } = useSubmitGuard();

  const fetchOrders = useCallback(async (opts?: { showSpinner?: boolean }) => {
    const showSpinner = opts?.showSpinner ?? true;
    if (showSpinner) setLoading(true);
    try {
      const data = await adminOrderApi.list(statusFilter || undefined, page, pageSize, showError);
      setOrders(data.content);
      setTotal(data.totalElements);
    } finally {
      if (showSpinner) setLoading(false);
    }
  }, [statusFilter, page, pageSize, showError]);

  useEffect(() => { fetchOrders(); }, [fetchOrders]);

  const handleConfirmAction = (): void => {
    if (!actionTarget) return;
    guardAction(async () => {
      try {
        if (actionTarget.action === 'ship') {
          await adminOrderApi.ship(actionTarget.order.id, showError);
          showSuccess(`Order #${actionTarget.order.id} marked shipped.`);
        } else {
          await adminOrderApi.deliver(actionTarget.order.id, showError);
          showSuccess(`Order #${actionTarget.order.id} marked delivered.`);
        }
        setActionTarget(null);
        fetchOrders({ showSpinner: false });
      } catch {
        // showError already called
      }
    });
  };

  return (
    <Box sx={{ p: 3 }}>

      <AdminListHeader title="Order Fulfillment" subtitle={`${total} order${total !== 1 ? 's' : ''}`} />

      {/* Filter */}
      <Stack direction="row" spacing={1.5} sx={{ mb: 2 }}>
        <Select
          value={statusFilter}
          onChange={e => { setStatusFilter(e.target.value as OrderStatus | ''); setPage(0); }}
          displayEmpty
          size="small"
          sx={{ minWidth: 200 }}
        >
          <MenuItem value="">All statuses</MenuItem>
          {ALL_STATUSES.map(status => (
            <MenuItem key={status} value={status}>{ORDER_STATUS_LABELS[status]}</MenuItem>
          ))}
        </Select>
      </Stack>

      {/* Table */}
      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 700 }}>Order #</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Placed</TableCell>
              <TableCell sx={{ fontWeight: 700 }} align="center">Items</TableCell>
              <TableCell sx={{ fontWeight: 700 }} align="right">Total</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Status</TableCell>
              <TableCell sx={{ fontWeight: 700 }} align="right">Actions</TableCell>
            </TableRow>
          </TableHead>

          <TableBody>
            {loading || orders.length === 0 ? (
              <TableStatusRow
                loading={loading}
                isEmpty={orders.length === 0}
                emptyMessage={
                  statusFilter ? `No orders are currently ${ORDER_STATUS_LABELS[statusFilter].toLowerCase()}.` : 'No orders yet.'
                }
                colSpan={6}
              />
            ) : (
              orders.map(order => {
                const placedAt = order.statusHistory[0]?.occurredAt;
                const itemCount = order.lines.reduce((sum, line) => sum + line.quantity, 0);
                const canShip = order.status === 'CONFIRMED';
                const canDeliver = order.status === 'SHIPPED';

                return (
                  <TableRow key={order.id} hover>
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>#{order.id}</Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color="text.secondary">
                        {placedAt ? formatOrderDate(placedAt) : '—'}
                      </Typography>
                    </TableCell>
                    <TableCell align="center">
                      <Typography variant="body2" color="text.secondary">{itemCount}</Typography>
                    </TableCell>
                    <TableCell align="right">
                      <Typography variant="body2">{formatPrice(order.total)}</Typography>
                    </TableCell>
                    <TableCell>
                      <Stack spacing={0.5} alignItems="flex-start">
                        <Chip
                          label={ORDER_STATUS_LABELS[order.status]}
                          color={ORDER_STATUS_COLORS[order.status]}
                          variant="outlined"
                          size="small"
                        />
                        {/* Only shown when it adds information the order-status chip above doesn't
                            already carry (a FAILED order already reads "Payment Failed"; DECLINED
                            just gets the why via tooltip). CONFIRMED+/PENDING payment states carry
                            nothing new here, so no chip renders for those. */}
                        {order.paymentStatus === 'DECLINED' && (
                          <Tooltip title={order.paymentFailureMessage ?? 'Payment declined'}>
                            <Chip label="Declined" color="error" size="small" />
                          </Tooltip>
                        )}
                        {order.paymentStatus === 'REFUNDED' && (
                          <Tooltip title={`${formatPrice(order.total)} refunded`}>
                            <Chip label="Refunded" color="secondary" size="small" />
                          </Tooltip>
                        )}
                      </Stack>
                    </TableCell>
                    <TableCell align="right">
                      <Tooltip title={canShip ? 'Mark Shipped' : 'Only valid from Confirmed'}>
                        <span>
                          <IconButton
                            size="small"
                            color="primary"
                            disabled={!canShip}
                            onClick={() => setActionTarget({ order, action: 'ship' })}
                          >
                            <LocalShippingIcon fontSize="small" />
                          </IconButton>
                        </span>
                      </Tooltip>
                      <Tooltip title={canDeliver ? 'Mark Delivered' : 'Only valid from Shipped'}>
                        <span>
                          <IconButton
                            size="small"
                            color="success"
                            disabled={!canDeliver}
                            onClick={() => setActionTarget({ order, action: 'deliver' })}
                          >
                            <AssignmentTurnedInIcon fontSize="small" />
                          </IconButton>
                        </span>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                );
              })
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

      <ConfirmDialog
        open={actionTarget !== null}
        title={actionTarget?.action === 'ship' ? 'Mark Order Shipped' : 'Mark Order Delivered'}
        message={
          actionTarget?.action === 'ship'
            ? `Mark order #${actionTarget?.order.id} as shipped?`
            : `Mark order #${actionTarget?.order.id} as delivered? This is the terminal state — it can't be undone.`
        }
        confirmLabel={actionTarget?.action === 'ship' ? 'Mark Shipped' : 'Mark Delivered'}
        loading={actionLoading}
        onConfirm={handleConfirmAction}
        onCancel={() => setActionTarget(null)}
      />
    </Box>
  );
}
