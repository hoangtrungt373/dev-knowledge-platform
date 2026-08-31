import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Pagination,
  Paper,
  Stack,
  Tab,
  Tabs,
  Typography,
} from '@mui/material';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import { useNotification } from '@shared/contexts/NotificationContext';
import { orderApi } from '../../api/orderApi';
import { Order } from '../../types';
import OrderLineRow from '../../components/orders/OrderLineRow';
import { ORDER_STATUS_COLORS, ORDER_STATUS_LABELS, ORDER_TAB_GROUPS, formatOrderDate } from '../../utils/orderStatus';
import { formatPrice } from '../../utils/format';

const PAGE_SIZE = 10;

export default function OrderHistoryPage(): JSX.Element {
  const navigate = useNavigate();
  const { showError } = useNotification();
  const [orders, setOrders] = useState<Order[] | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0); // zero-based, matches the backend's own Pageable convention
  const [loading, setLoading] = useState(true);
  // Status tabs (post-Epic-3 follow-up) — 'all' sends no status filter at all.
  const [tabKey, setTabKey] = useState<string>('all');
  const activeGroup = ORDER_TAB_GROUPS.find(g => g.key === tabKey) ?? ORDER_TAB_GROUPS[0];

  useEffect(() => {
    setLoading(true);
    orderApi.list(page, PAGE_SIZE, activeGroup.statuses)
      .then((result) => {
        setOrders(result.content);
        setTotalPages(result.totalPages);
      })
      .catch((err) => showError(err instanceof Error ? err.message : 'Could not load your orders.'))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, tabKey]);

  const handleTabChange = (key: string): void => {
    setTabKey(key);
    setPage(0); // a filter change always restarts pagination — the old page number may not exist under the new filter
  };

  if (loading && orders === null) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '50vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  // Truly no orders at all yet (checked only against the 'all' tab, never a filtered one — a
  // filtered tab with zero matches still has orders elsewhere, so it gets the inline empty state
  // below instead, keeping the tabs themselves visible so the shopper can switch back to All).
  if (tabKey === 'all' && orders !== null && orders.length === 0) {
    return (
      <Box sx={{ p: 3, textAlign: 'center', maxWidth: 500, mx: 'auto', mt: 6 }}>
        <ReceiptLongOutlinedIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
        <Typography variant="h6" sx={{ mb: 1 }}>No orders yet</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Once you place an order, you'll see it here.
        </Typography>
        <Button variant="contained" onClick={() => navigate('/shop')}>Go to Shop</Button>
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3, width: '80%', mx: 'auto' }}>
      <Typography variant="h5" fontWeight={700} sx={{ mb: 3 }}>Your Orders</Typography>

      <Tabs
        value={tabKey}
        onChange={(_, value) => handleTabChange(value)}
        sx={{ mb: 3, bgcolor: 'background.paper', borderRadius: 1 }}
        variant="scrollable"
        scrollButtons="auto"
      >
        {ORDER_TAB_GROUPS.map(group => (
          <Tab key={group.key} value={group.key} label={group.label} />
        ))}
      </Tabs>

      {orders !== null && orders.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', mt: 4, mb: 3 }}>
          No orders in this category.
        </Typography>
      ) : (
        <Stack spacing={2} sx={{ mb: 3 }}>
          {orders?.map(order => (
            <OrderCard key={order.id} order={order} onView={() => navigate(`/orders/${order.id}`)} />
          ))}
        </Stack>
      )}

      {totalPages > 1 && (
        <Stack direction="row" justifyContent="center">
          <Pagination
            count={totalPages}
            page={page + 1}
            onChange={(_, value) => setPage(value - 1)}
            color="primary"
          />
        </Stack>
      )}
    </Box>
  );
}

function OrderCard({ order, onView }: { order: Order; onView: () => void }): JSX.Element {
  const placedAt = order.statusHistory[0]?.occurredAt;

  return (
    <Paper variant="outlined" sx={{ p: 2.5 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2} sx={{ mb: 2 }}>
        <Box>
          {placedAt && (
            <Typography variant="body2" color="text.secondary">Placed {formatOrderDate(placedAt)}</Typography>
          )}
        </Box>
        <Stack alignItems="flex-end" spacing={1}>
          <Chip
            label={ORDER_STATUS_LABELS[order.status]}
            color={ORDER_STATUS_COLORS[order.status]}
            size="small"
          />
          <Button size="small" onClick={onView}>View Details</Button>
        </Stack>
      </Stack>

      <Stack spacing={2} divider={<Divider />}>
        {order.lines.map(line => (
          <OrderLineRow key={line.variantId} line={line} />
        ))}
      </Stack>

      <Divider sx={{ my: 2 }} />

      <Stack direction="row" justifyContent="flex-end">
        <Typography variant="subtitle1" fontWeight={700} color="error.main">Total: {formatPrice(order.total)}</Typography>
      </Stack>
    </Paper>
  );
}
