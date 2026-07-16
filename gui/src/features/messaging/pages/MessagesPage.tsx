import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Box, IconButton, Stack, Tooltip, Typography } from '@mui/material';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import HomeOutlinedIcon from '@mui/icons-material/HomeOutlined';
import LightModeIcon from '@mui/icons-material/LightMode';
import { ThemeMode } from '@app/theme';
import UserAvatar from '@friends/components/UserAvatar';
import { userApi } from '@friends/api/userApi';
import { UserSummary } from '@friends/types';
import { useNotification } from '@shared/contexts/NotificationContext';
import ConversationList from '../components/ConversationList';
import MessageList from '../components/MessageList';
import MessageComposer from '../components/MessageComposer';
import { useDmThreads } from '../hooks/useDmThreads';
import { DmTarget, useDmThread } from '../hooks/useDmThread';
import { useStompConnection } from '../context/StompConnectionContext';
import { DmThread } from '../types';

interface Props {
  mode: ThemeMode;
  onToggleMode: () => void;
}

/**
 * Full-viewport DM page (NavBar hidden), mirroring @chat/pages/ChatPage's shell: a left
 * conversation list and a right message pane, driven by three routes —
 * `/messages` (list only), `/messages/new/:recipientUuid` (no thread yet), `/messages/:threadId`
 * (existing thread).
 */
export default function MessagesPage({ mode, onToggleMode }: Props): JSX.Element {
  const { threadId: threadIdParam, recipientUuid: newRecipientUuid } = useParams<{
    threadId?: string;
    recipientUuid?: string;
  }>();
  const navigate = useNavigate();
  const { showError } = useNotification();
  const { connected } = useStompConnection();

  const { threads, loading: threadsLoading } = useDmThreads();
  const [pendingRecipient, setPendingRecipient] = useState<UserSummary | null>(null);

  const activeThreadId = threadIdParam ? Number(threadIdParam) : null;
  const activeThread = activeThreadId != null ? threads.find(t => t.id === activeThreadId) ?? null : null;

  const target: DmTarget | null = newRecipientUuid
    ? { kind: 'new', recipientUuid: newRecipientUuid }
    : activeThreadId != null && activeThread
      ? { kind: 'thread', threadId: activeThreadId, recipientUuid: activeThread.otherUser.userUuid }
      : null;

  const { messages, loading: messagesLoading, send } = useDmThread(target, resolvedThreadId => {
    navigate(`/messages/${resolvedThreadId}`, { replace: true });
  });

  // 'new' conversations aren't in `threads` yet — fetch the recipient's public profile for the
  // header (avatar + name). Re-fetches per recipientUuid change, not on every render.
  useEffect(() => {
    if (!newRecipientUuid) {
      setPendingRecipient(null);
      return;
    }
    let cancelled = false;
    userApi.getUserById(newRecipientUuid, showError).then(user => {
      if (cancelled) return;
      setPendingRecipient({
        userUuid: newRecipientUuid,
        username: user.username,
        firstName: user.firstName ?? null,
        lastName: user.lastName ?? null,
        profilePicture: user.profilePicture ?? null,
        status: user.status,
      });
    });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [newRecipientUuid]);

  const headerUser = newRecipientUuid ? pendingRecipient : activeThread?.otherUser ?? null;

  const handleSelectThread = (thread: DmThread) => navigate(`/messages/${thread.id}`);

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      {/* ── Left conversation list ─────────────────────────────────── */}
      <Box
        sx={{
          width: 320,
          flexShrink: 0,
          borderRight: 1,
          borderColor: 'divider',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', p: 2 }}>
          <Stack direction="row" alignItems="center" spacing={0.5}>
            <Tooltip title="Back to Dashboard">
              <IconButton size="small" onClick={() => navigate('/dashboard')}>
                <HomeOutlinedIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            <Typography variant="h6" fontWeight={700}>Messages</Typography>
          </Stack>
          <Tooltip title={mode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}>
            <IconButton size="small" onClick={onToggleMode}>
              {mode === 'dark' ? <LightModeIcon fontSize="small" /> : <DarkModeIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
        </Box>
        <Box sx={{ flex: 1, overflowY: 'auto' }}>
          <ConversationList
            threads={threads}
            loading={threadsLoading}
            activeThreadId={activeThreadId}
            onSelectThread={handleSelectThread}
          />
        </Box>
      </Box>

      {/* ── Right message pane ─────────────────────────────────────── */}
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', bgcolor: 'background.default' }}>
        {!target || !headerUser ? (
          <Box sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Typography color="text.secondary">
              {threadsLoading ? '' : 'Select a conversation to start messaging.'}
            </Typography>
          </Box>
        ) : (
          <>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, p: 2, borderBottom: 1, borderColor: 'divider' }}>
              <UserAvatar user={headerUser} size={36} />
              <Typography variant="subtitle1" fontWeight={600}>
                {[headerUser.firstName, headerUser.lastName].filter(Boolean).join(' ') || headerUser.username}
              </Typography>
            </Box>
            {messagesLoading ? (
              <Box sx={{ flex: 1 }} />
            ) : (
              <MessageList messages={messages} />
            )}
            <MessageComposer disabled={!connected} onSend={send} />
          </>
        )}
      </Box>
    </Box>
  );
}
