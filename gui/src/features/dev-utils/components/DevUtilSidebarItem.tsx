import { ListItemButton, ListItemIcon, ListItemText, Tooltip } from '@mui/material';
import { alpha } from '@mui/material/styles';

import { OperationConfig } from '../config/operations';

interface DevUtilSidebarItemProps {
  operation: OperationConfig;
  isSelected: boolean;
  /** Collapsed sidebar has no room for the label — a `Tooltip` carries it instead, same
   * "icon-only row, label in a Tooltip" shape `AdminLayout.tsx`'s own collapsed sidebar uses. */
  collapsed: boolean;
  onSelect: () => void;
}

/** One row in `DevUtilsPage.tsx`'s own sidebar `List` — extracted out of that page's own
 * `.map()` callback purely to keep that file focused on page-level layout/routing rather than a
 * single list item's own styling. The caller still owns the `key` prop (React requires it at the
 * `.map()` call site, not inside this component). */
export default function DevUtilSidebarItem({
  operation,
  isSelected,
  collapsed,
  onSelect,
}: DevUtilSidebarItemProps): JSX.Element {
  const itemButton = (
    <ListItemButton
      selected={isSelected}
      onClick={onSelect}
      sx={{
        borderRadius: 1,
        mx: 0.5,
        mb: 0.25,
        justifyContent: collapsed ? 'center' : 'flex-start',
        // 1.5, not 2 — nudges the row (icon + label together) a little left so the icon lines up
        // with the search icon in the search box above, per request.
        px: collapsed ? 1 : 1.5,
        // Target `&.Mui-selected` explicitly, not a plain `bgcolor` on the root —
        // ListItemButton's own baked-in selected-state rule
        // (`&.Mui-selected { backgroundColor: action.selected }`) has *higher* CSS specificity
        // (root class + Mui-selected class) than a plain `bgcolor` on the component's own root
        // class alone, so it was silently winning over this override regardless of the
        // `isSelected` conditional already choosing the right value in JS — matching MUI's own
        // selector exactly is what makes this override actually take effect.
        '&.Mui-selected': {
          bgcolor: theme => alpha(theme.palette.primary.main, 0.16),
        },
        '&.Mui-selected:hover': {
          bgcolor: theme => alpha(theme.palette.primary.main, 0.24),
        },
        '&:hover': {
          bgcolor: 'action.hover',
        },
      }}
    >
      <ListItemIcon sx={{ minWidth: collapsed ? 0 : 32, justifyContent: 'center', color: 'grey.500', mr: 0.5 }}>
        {operation.icon}
      </ListItemIcon>
      {/* fontWeight is fixed regardless of selection — only the background above distinguishes
          the selected item now, per request. */}
      {!collapsed && (
        <ListItemText primary={operation.label} primaryTypographyProps={{ variant: 'body2', fontWeight: 600 }} />
      )}
    </ListItemButton>
  );

  return collapsed ? (
    <Tooltip title={operation.label} placement="right">
      {itemButton}
    </Tooltip>
  ) : (
    itemButton
  );
}
