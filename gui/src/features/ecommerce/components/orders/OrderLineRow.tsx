import { Link } from 'react-router-dom';
import { Box, Stack, Typography } from '@mui/material';
import { OrderLine } from '../../types';
import { formatPrice, formatVariantLabel } from '../../utils/format';
import Thumbnail from '../Thumbnail';

/**
 * One purchased line, shared by `OrderHistoryPage`/`OrderDetailPage` (both render an order's
 * lines identically) — `<thumbnail> <product name / "Variant: ..." / "Quantity: xN"> <lineTotal>`.
 *
 * Only the thumbnail and the product name link to `/shop/${productSlug}` — not the variant/
 * quantity lines below the name, and not the price. Reconciled here (a post-audit cleanup) with
 * `CartPage`'s own cart-line `Link`, which settled on the identical "only thumbnail+name
 * navigate" rule after a bug report about its price/variant area being unintentionally clickable
 * too — this component used to wrap its *entire* info block (name + variant + quantity) instead,
 * an inconsistency the audit flagged as accidental drift rather than a deliberate difference. Two
 * separate `<Link>`s here (thumbnail, then name), not one wrapping both the way `CartPage`'s
 * side-by-side thumbnail+name does — this layout stacks the name *above* the variant/quantity
 * lines rather than beside the thumbnail, so one `Link` can't cleanly cover both; both still point
 * at the same URL. Only rendered as links at all when `productSlug` is present, since a
 * since-deleted variant/product resolves it (along with `attributes`/`primaryImageUrl`) to `null`,
 * per `OrderLineResponse`'s own contract.
 */
export default function OrderLineRow({ line }: { line: OrderLine }): JSX.Element {
  const variantLabel = formatVariantLabel(line.attributes);

  const thumbnail = <Thumbnail imageUrl={line.primaryImageUrl} alt={line.productName} />;
  const nameText = (
    <Typography
      variant="body2"
      fontWeight={600}
      noWrap
      sx={line.productSlug ? { '&:hover': { textDecoration: 'underline' } } : undefined}
    >
      {line.productName}
    </Typography>
  );

  return (
    <Stack direction="row" alignItems="center" spacing={2}>
      {line.productSlug ? (
        <Link to={`/shop/${line.productSlug}`} style={{ display: 'flex', color: 'inherit', textDecoration: 'none' }}>
          {thumbnail}
        </Link>
      ) : (
        thumbnail
      )}

      <Box sx={{ flex: 1, minWidth: 0 }}>
        {line.productSlug ? (
          <Link to={`/shop/${line.productSlug}`} style={{ display: 'block', color: 'inherit', textDecoration: 'none' }}>
            {nameText}
          </Link>
        ) : (
          nameText
        )}
        {variantLabel && (
          <Typography variant="body2" color="text.secondary">Variant: {variantLabel}</Typography>
        )}
        <Typography variant="body2" color="text.secondary">Quantity: x{line.quantity}</Typography>
      </Box>

      <Typography variant="body2" fontWeight={600} color="error.main" sx={{ flexShrink: 0 }}>
        {formatPrice(line.lineTotal)}
      </Typography>
    </Stack>
  );
}
