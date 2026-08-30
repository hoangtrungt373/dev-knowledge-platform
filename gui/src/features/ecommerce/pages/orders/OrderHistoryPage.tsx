import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Pagination,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import { useNotification } from '@shared/contexts/NotificationContext';
import { orderApi } from '../../api/orderApi';
import { Order } from '../../types';
import { ORDER_STATUS_COLORS, ORDER_STATUS_LABELS, formatOrderDate } from '../../utils/orderStatus';

const PAGE_SIZE = 10;

function formatPrice(value: number): string {
  return `$${value.toFixed(2)}`;
}

export default function OrderHistoryPage(): JSX.Element {
  const navigate = useNavigate();
  const { showError } = useNotification();
  const [orders, setOrders] = useState<Order[] | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0); // zero-based, matches the backend's own Pageable convention
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    orderApi.list(page, PAGE_SIZE)
      .then((result) => {
        setOrders(result.content);
        setTotalPages(result.totalPages);
      })
      .catch((err) => showError(err instanceof Error ? err.message : 'Could not load your orders.'))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  if (loading && orders === null) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '50vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (orders !== null && orders.length === 0) {
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
    <Box sx={{ p: 3, maxWidth: 800, mx: 'auto' }}>
      <Typography variant="h5" fontWeight={700} sx={{ mb: 3 }}>Your Orders</Typography>

      <Stack spacing={2} sx={{ mb: 3 }}>
        {orders?.map(order => (
          <OrderCard key={order.id} order={order} onView={() => navigate(`/orders/${order.id}`)} />
        ))}
      </Stack>

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
  const itemCount = order.lines.reduce((sum, line) => sum + line.quantity, 0);

  return (
    <Paper variant="outlined" sx={{ p: 2.5 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2}>
        <Box>
          <Typography variant="subtitle1" fontWeight={700}>Order #{order.id}</Typography>
          {placedAt && (
            <Typography variant="body2" color="text.secondary">Placed {formatOrderDate(placedAt)}</Typography>
          )}
          <Typography variant="body2" color="text.secondary">
            {itemCount} item{itemCount === 1 ? '' : 's'} · {formatPrice(order.total)}
          </Typography>
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
    </Paper>
  );
}
