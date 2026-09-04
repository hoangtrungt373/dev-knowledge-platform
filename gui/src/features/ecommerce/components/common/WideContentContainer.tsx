import { ReactNode } from 'react';
import { Box, SxProps, Theme } from '@mui/material';

interface WideContentContainerProps {
  children: ReactNode;
  sx?: SxProps<Theme>;
}

/** The shared "wide top-level page content" wrapper for `@ecommerce`'s shopper-facing pages
 * (`ShopPage`, `CartPage`, `CheckoutPage`, `ProductDetailPage`) — fluid at 80% of the viewport up
 * to a 1400px cap, so content doesn't stretch unreadably wide on very wide monitors while still
 * behaving exactly like a plain `width: '80%'` on anything narrower than ~1750px. Extracted after
 * an ecommerce style audit found two different width strategies for the same "wide storefront
 * page" role (plain `width: '80%'` on three pages, a flat `maxWidth: 1400` with no fluid behavior
 * on the fourth) — see `gui/CLAUDE.md`'s ecommerce style-audit note. Always includes the page's
 * own `p: 3` padding, so callers shouldn't add their own. Not for pages nested inside
 * `AccountLayout` (`AddressBookPage`/`OrderHistoryPage`/`OrderDetailPage`) — that layout already
 * applies its own width cap, and stacking a second one here would compound into a visibly narrow,
 * off-center column. */
export default function WideContentContainer({ children, sx }: WideContentContainerProps): JSX.Element {
  return (
    <Box sx={{ p: 3, width: '80%', maxWidth: 1400, mx: 'auto', ...sx }}>
      {children}
    </Box>
  );
}
