import { CircularProgress, TableCell, TableRow, Typography } from '@mui/material';

interface Props {
  loading: boolean;
  isEmpty: boolean;
  emptyMessage: string;
  /** Must match the surrounding `<TableHead>`'s own column count — there's no way to derive this
   * automatically from inside a single `<TableRow>`. */
  colSpan: number;
}

/**
 * The loading-spinner-row / empty-state-row pair repeated identically (only `emptyMessage`/
 * `colSpan` differing) across every admin list table in this feature
 * (`ProductCategoryListPage`/`ProductTagListPage`/`ProductListPage`/`AdminOrderListPage`).
 * Renders `null` when neither loading nor empty, so a caller's `<TableBody>` reads as
 * `{loading || isEmpty ? <TableStatusRow .../> : items.map(...)}` — same conditional shape every
 * one of those four pages already used, just without re-typing the two `<TableRow>` bodies each
 * time.
 */
export default function TableStatusRow({ loading, isEmpty, emptyMessage, colSpan }: Props): JSX.Element | null {
  if (loading) {
    return (
      <TableRow>
        <TableCell colSpan={colSpan} align="center" sx={{ py: 6 }}>
          <CircularProgress size={28} />
        </TableCell>
      </TableRow>
    );
  }
  if (isEmpty) {
    return (
      <TableRow>
        <TableCell colSpan={colSpan} align="center" sx={{ py: 6 }}>
          <Typography variant="body2" color="text.secondary">{emptyMessage}</Typography>
        </TableCell>
      </TableRow>
    );
  }
  return null;
}
