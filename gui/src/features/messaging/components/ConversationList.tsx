import { Box, CircularProgress, List, ListItemButton, Typography } from '@mui/material';
import UserRow from '@friends/components/UserRow';
import { DmThread } from '../types';

interface Props {
  threads: DmThread[];
  loading: boolean;
  activeThreadId: number | null;
  onSelectThread: (thread: DmThread) => void;
}

function formatLastMessageAt(iso: string | null): string | undefined {
  if (!iso) return undefined;
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' });
}

/**
 * DM conversation list — presentational; the thread data (kept live by useDmThreads) is owned by
 * MessagesPage and passed down, so the page can also resolve the active thread's `otherUser` for
 * its header without a second fetch. Reuses @friends' UserRow for the row shape.
 */
export default function ConversationList({ threads, loading, activeThreadId, onSelectThread }: Props): JSX.Element {
  if (loading && threads.length === 0) {
    return (
      <Box sx={{ py: 4, textAlign: 'center' }}>
        <CircularProgress size={24} />
      </Box>
    );
  }

  if (threads.length === 0) {
    return (
      <Box sx={{ py: 4, px: 2, textAlign: 'center' }}>
        <Typography variant="body2" color="text.secondary">
          No conversations yet — start one from a friend's row on the Friends page.
        </Typography>
      </Box>
    );
  }

  return (
    <List disablePadding>
      {threads.map(thread => (
        <ListItemButton
          key={thread.id}
          selected={thread.id === activeThreadId}
          onClick={() => onSelectThread(thread)}
          sx={{ px: 1.5 }}
        >
          <UserRow user={thread.otherUser} subtitle={formatLastMessageAt(thread.lastMessageAt)} />
        </ListItemButton>
      ))}
    </List>
  );
}
