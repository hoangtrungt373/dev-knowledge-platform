import { IconButton, ListItemButton, ListItemIcon, ListItemText, Tooltip } from '@mui/material';
import { alpha } from '@mui/material/styles';
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorderOutlined';

import { OperationConfig } from '../config/operations';

interface DevUtilSidebarItemProps {
  operation: OperationConfig;
  isSelected: boolean;
  /** Collapsed sidebar has no room for the label — a `Tooltip` carries it instead, same
   * "icon-only row, label in a Tooltip" shape `AdminLayout.tsx`'s own collapsed sidebar uses. */
  collapsed: boolean;
  onSelect: () => void;
  /** Whether this operation is in the user's (client-side-only) favorites set — see
   * `hooks/useFavoriteOperations.ts`. */
  isFavorite: boolean;
  onToggleFavorite: () => void;
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
  isFavorite,
  onToggleFavorite,
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
        // The favorite button stays out of sight until the row is hovered/keyboard-focused — per
        // request. `opacity`, not `display`/`visibility: hidden`, so a keyboard user can still Tab
        // to it before hovering (`focus-within` covers that) — same convention
        // `@tasks/TaskRow.tsx`'s own hover-revealed "⋯"/drag-handle buttons already establish in
        // this codebase.
        '&:hover .dev-util-favorite-btn, &:focus-within .dev-util-favorite-btn': {
          opacity: 1,
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
      {/* No room for a second control in the collapsed, icon-only row — favoriting stays reachable
          via the expanded sidebar or the active-operation header either way. `stopPropagation` so
          toggling a favorite doesn't also select the row (same guard `@tasks/TaskRow.tsx`'s own
          directly-clickable row children already use for the identical reason). */}
      {!collapsed && (
        <IconButton
          size="small"
          className="dev-util-favorite-btn"
          onClick={e => {
            e.stopPropagation();
            onToggleFavorite();
          }}
          aria-label={isFavorite ? 'Remove from favorites' : 'Add to favorites'}
          sx={{ p: 0.5, ml: 0.5, opacity: 0, transition: 'opacity 0.15s ease' }}
        >
          {isFavorite ? (
            <StarIcon fontSize="small" sx={{ color: '#FFC107' }} />
          ) : (
            <StarBorderIcon fontSize="small" sx={{ color: '#FFC107' }} />
          )}
        </IconButton>
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
