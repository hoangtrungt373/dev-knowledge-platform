import { Link } from 'react-router-dom';
import { Box, Stack, Typography } from '@mui/material';
import ImageNotSupportedIcon from '@mui/icons-material/ImageNotSupported';
import { OrderLine } from '../../types';

function formatPrice(value: number): string {
  return `$${value.toFixed(2)}`;
}

/**
 * One purchased line, shared by `OrderHistoryPage`/`OrderDetailPage` (both render an order's
 * lines identically) — `<thumbnail> <product name / "Variant: ..." / "Quantity: xN"> <lineTotal>`.
 *
 * Only the thumbnail+info block links to `/shop/${productSlug}`, not the whole row (same
 * "don't make the price clickable too" fix `CartPage`'s own cart-line `Link` already applies) —
 * and only when `productSlug` is present at all, since a since-deleted variant/product resolves it
 * (along with `attributes`/`primaryImageUrl`) to `null`, per `OrderLineResponse`'s own contract.
 */
export default function OrderLineRow({ line }: { line: OrderLine }): JSX.Element {
  const variantLabel = line.attributes ? Object.values(line.attributes).join(' ') : '';

  const info = (
    <>
      <Box
        sx={{
          width: 64, height: 64, flexShrink: 0, bgcolor: 'action.hover', borderRadius: 1,
          display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden',
        }}
      >
        {line.primaryImageUrl ? (
          <Box
            component="img"
            src={line.primaryImageUrl}
            alt={line.productName}
            sx={{ width: '100%', height: '100%', objectFit: 'cover' }}
          />
        ) : (
          <ImageNotSupportedIcon sx={{ fontSize: 28, color: 'text.disabled' }} />
        )}
      </Box>

      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography variant="body2" fontWeight={600} noWrap>{line.productName}</Typography>
        {variantLabel && (
          <Typography variant="body2" color="text.secondary">Variant: {variantLabel}</Typography>
        )}
        <Typography variant="body2" color="text.secondary">Quantity: x{line.quantity}</Typography>
      </Box>
    </>
  );

  return (
    <Stack direction="row" alignItems="center" spacing={2}>
      {line.productSlug ? (
        <Link
          to={`/shop/${line.productSlug}`}
          style={{ display: 'flex', alignItems: 'center', gap: 16, flex: 1, minWidth: 0, color: 'inherit', textDecoration: 'none' }}
        >
          {info}
        </Link>
      ) : (
        <Stack direction="row" alignItems="center" spacing={2} sx={{ flex: 1, minWidth: 0 }}>
          {info}
        </Stack>
      )}

      <Typography variant="body2" fontWeight={600} color="error.main" sx={{ flexShrink: 0 }}>
        {formatPrice(line.lineTotal)}
      </Typography>
    </Stack>
  );
}
