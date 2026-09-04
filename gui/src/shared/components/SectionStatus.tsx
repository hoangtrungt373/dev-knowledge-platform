import { Box, CircularProgress, SxProps, Theme, Typography } from '@mui/material';

interface SectionStatusProps {
  loading: boolean;
  isEmpty: boolean;
  emptyMessage: string;
  /** Default `28` matches the majority usage (`@friends`'s 5 lists); `@messaging/
   * ConversationList` passes `24` for its own narrower sidebar column. */
  spinnerSize?: number;
  /** Default `6` (theme spacing units) matches the majority usage; `@messaging/ConversationList`
   * passes `4` for its own narrower sidebar column. */
  py?: number;
  sx?: SxProps<Theme>;
}

/**
 * The `Box`-based sibling of `TableStatusRow` — a centered spinner or a centered "nothing here"
 * message, for a plain list/section rather than a `<TableBody>`. Extracted after a style audit
 * found the exact same `Box sx={{py:6,textAlign:'center'}}` + `CircularProgress`/`Typography` pair
 * duplicated identically across `@friends`'s 5 list components (`UserSearch`/`FriendsList`/
 * `BlockedUsersList`/`FriendRequestsOutgoing`/`FriendRequestsIncoming`) and near-identically in
 * `@messaging/components/ConversationList.tsx` — see `gui/CLAUDE.md`'s style-audit note. Renders
 * `null` when neither loading nor empty, same convention `TableStatusRow` already uses, so a
 * caller reads as `{loading || isEmpty ? <SectionStatus .../> : items.map(...)}`.
 */
export default function SectionStatus({
  loading,
  isEmpty,
  emptyMessage,
  spinnerSize = 28,
  py = 6,
  sx,
}: SectionStatusProps): JSX.Element | null {
  if (loading) {
    return (
      <Box sx={{ py, textAlign: 'center', ...sx }}>
        <CircularProgress size={spinnerSize} />
      </Box>
    );
  }
  if (isEmpty) {
    return (
      <Box sx={{ py, textAlign: 'center', ...sx }}>
        <Typography color="text.secondary">{emptyMessage}</Typography>
      </Box>
    );
  }
  return null;
}
