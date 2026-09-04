import { ReactNode } from 'react';
import { Paper, Stack, SxProps, Theme, Typography } from '@mui/material';

interface SectionPanelProps {
  /** Renders the canonical `subtitle2`/700 section heading — the convention already used by most
   * of this feature's panels (ProductFormPage, ShopPage's sidebar, the variant/image editors).
   * Omit for a panel with no heading of its own (e.g. AddressBookPage's list panel, which already
   * has its own bespoke header row as a child). */
  title?: string;
  /** An element (typically a `Button`) rendered on the same row as `title`, right-aligned — e.g.
   * "Upload Image"/"Add Variant". Ignored if `title` isn't set. */
  action?: ReactNode;
  children: ReactNode;
  /** Uniform padding on all four sides, in theme spacing units. Default `2` matches the majority
   * of this feature's panels; pass `sx` on top for anything asymmetric (e.g. a different left
   * padding) or to override a side after the fact. */
  padding?: number;
  sx?: SxProps<Theme>;
}

/** The shared "bordered panel of content" wrapper — a plain `Paper variant="outlined"` (the
 * theme's own default Paper look: 1px border, subtle shadow in light mode, 6px radius) plus an
 * optional title/action header row. Extracted after an ecommerce style audit found two competing
 * visual languages for the same semantic role — some panels used this bordered `Paper` look,
 * others a borderless `Box` with `bgcolor: 'background.paper'` and a 12px radius — with no
 * semantic rule distinguishing them (see gui/CLAUDE.md's ecommerce style-audit note). Bordered was
 * chosen as the single convention since it's already the theme's own default `Paper` styling and
 * already the majority usage across this feature.
 *
 * <p>Deliberately does not standardize Checkout/OrderDetail's own panel-title convention
 * (`subtitle1`/600, vs. this component's `subtitle2`/700) — that's a separate, still-open style
 * question (undocumented split between admin/Shop panels and Checkout/OrderDetail panels), so
 * those two pages keep their own plain `Paper` usage untouched for now rather than silently
 * changing their heading weight/size as a side effect of this extraction. */
export default function SectionPanel({ title, action, children, padding = 2, sx }: SectionPanelProps): JSX.Element {
  return (
    <Paper variant="outlined" sx={{ p: padding, ...sx }}>
      {title && (
        action ? (
          <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1.5 }}>
            <Typography variant="subtitle2" fontWeight={700}>{title}</Typography>
            {action}
          </Stack>
        ) : (
          <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1.5 }}>{title}</Typography>
        )
      )}
      {children}
    </Paper>
  );
}
