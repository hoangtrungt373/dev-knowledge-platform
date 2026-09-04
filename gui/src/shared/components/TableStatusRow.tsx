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
 * `colSpan` differing) across every admin list table that renders one — originally extracted
 * within `@ecommerce` (`ProductCategoryListPage`/`ProductTagListPage`/`ProductListPage`/
 * `AdminOrderListPage`/etc.), then promoted here once `@content`'s own list pages
 * (`CategoryListPage`/`TagListPage`/`QuestionAnswerListPage`) turned out to duplicate the exact
 * same markup verbatim rather than importing it — see `gui/CLAUDE.md`'s style-audit note. Renders
 * `null` when neither loading nor empty, so a caller's `<TableBody>` reads as
 * `{loading || isEmpty ? <TableStatusRow .../> : items.map(...)}`.
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
