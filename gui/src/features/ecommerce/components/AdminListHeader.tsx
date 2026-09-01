import { ReactNode } from 'react';
import { Box, Button, Stack, Typography } from '@mui/material';

interface Props {
  title: string;
  subtitle: string;
  action?: {
    label: string;
    icon?: ReactNode;
    onClick: () => void;
  };
}

/**
 * The title/count/"New X" header row repeated identically (title left, optional action button
 * right) across every admin list page in this feature (`ProductCategoryListPage`/
 * `ProductTagListPage`/`ProductListPage`; `AdminOrderListPage` omits `action` — it has no create
 * flow of its own). `action` is optional rather than every page rendering its own `Button` so a
 * page with no action (like `AdminOrderListPage`) doesn't need a conditional around this component
 * at all, just an omitted prop.
 */
export default function AdminListHeader({ title, subtitle, action }: Props): JSX.Element {
  return (
    <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2.5 }}>
      <Box>
        <Typography variant="h5" fontWeight={700}>{title}</Typography>
        <Typography variant="body2" color="text.secondary">{subtitle}</Typography>
      </Box>
      {action && (
        <Button variant="contained" startIcon={action.icon} onClick={action.onClick}>
          {action.label}
        </Button>
      )}
    </Stack>
  );
}
