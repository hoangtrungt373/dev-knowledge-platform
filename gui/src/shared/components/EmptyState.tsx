import { ReactNode } from 'react';
import { Box, Button, Typography } from '@mui/material';

interface EmptyStateProps {
  /** e.g. `<ShoppingCartOutlinedIcon sx={{ fontSize: 64, color: 'text.disabled' }} />` — sized and
   * colored by the caller, not this component, since a handful of callers reuse an icon they
   * already imported for another purpose on the same page. */
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: { label: string; onClick: () => void };
}

/** The centered "nothing to show here" block — icon, title, optional description, optional CTA —
 * used for an empty list/cart/history and for a "not found" detail page alike. Extracted once the
 * same role turned up with three different completeness levels across the ecommerce feature (some
 * pages had the icon, some didn't; "not found" pages sometimes skipped the whole centered
 * treatment) — see gui/CLAUDE.md's ecommerce style-audit note. Always centered with the same
 * `maxWidth: 500, mx: 'auto', mt: 6` box every prior instance already agreed on. */
export default function EmptyState({ icon, title, description, action }: EmptyStateProps): JSX.Element {
  return (
    <Box sx={{ p: 3, textAlign: 'center', maxWidth: 500, mx: 'auto', mt: 6 }}>
      {icon && <Box sx={{ mb: 2 }}>{icon}</Box>}
      <Typography variant="h6" sx={{ mb: description || action ? 1 : 0 }}>{title}</Typography>
      {description && (
        <Typography variant="body2" color="text.secondary" sx={{ mb: action ? 3 : 0 }}>
          {description}
        </Typography>
      )}
      {action && (
        <Button variant="contained" onClick={action.onClick}>{action.label}</Button>
      )}
    </Box>
  );
}
