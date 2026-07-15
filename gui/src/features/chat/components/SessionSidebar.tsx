import { useEffect, useState } from 'react';
import {
  Box,
  Button,
  CircularProgress,
  Divider,
  IconButton,
  Skeleton,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutline';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import LightModeIcon from '@mui/icons-material/LightMode';
import LogoutIcon from '@mui/icons-material/Logout';
import { useNavigate } from 'react-router-dom';
import { chatApi } from '../api/chatApi';
import { authService } from '@auth/services/authService';
import { ChatSessionSummary } from '../types';
import { ThemeMode } from '@app/theme';

interface Props {
  activeSessionId: number | null;
  onNewChat: () => void;
  /** Incremented by ChatPage after a turn completes to trigger a refresh. */
  refreshTick: number;
  mode: ThemeMode;
  onToggleMode: () => void;
}

interface SessionGroup {
  label: string;
  items: ChatSessionSummary[];
}

function groupByDate(sessions: ChatSessionSummary[]): SessionGroup[] {
  const now = new Date();
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const yesterdayStart = todayStart - 86_400_000;

  const groups: SessionGroup[] = [
    { label: 'Today', items: [] },
    { label: 'Yesterday', items: [] },
    { label: 'Earlier', items: [] },
  ];

  for (const s of sessions) {
    const t = new Date(s.lastActivityAt).getTime();
    if (t >= todayStart) groups[0].items.push(s);
    else if (t >= yesterdayStart) groups[1].items.push(s);
    else groups[2].items.push(s);
  }

  return groups.filter(g => g.items.length > 0);
}

/** Left-side panel listing all chat sessions for the current user. */
export default function SessionSidebar({
  activeSessionId,
  onNewChat,
  refreshTick,
  mode,
  onToggleMode,
}: Props) {
  const navigate = useNavigate();
  const [sessions, setSessions] = useState<ChatSessionSummary[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    chatApi.listSessions().then(list => {
      setSessions(list);
      setLoading(false);
    }).catch(() => setLoading(false));
  }, [refreshTick]);

  const handleLogout = () => {
    authService.logout();
    navigate('/login');
  };

  const groups = groupByDate(sessions);

  return (
    <Box
      sx={{
        width: 260,
        flexShrink: 0,
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        borderRight: '1px solid',
        borderColor: 'divider',
        bgcolor: 'background.paper',
      }}
    >
      {/* Header */}
      <Box sx={{ p: 1.5, pb: 1 }}>
        <Button
          fullWidth
          variant="outlined"
          startIcon={<AddIcon />}
          onClick={onNewChat}
          sx={{ justifyContent: 'flex-start', borderRadius: 1.5 }}
        >
          New chat
        </Button>
      </Box>

      <Divider />

      {/* Session list */}
      <Box sx={{ flex: 1, overflowY: 'auto', py: 1 }}>
        {loading ? (
          <Stack spacing={0.5} sx={{ px: 1.5, pt: 0.5 }}>
            {[...Array(5)].map((_, i) => (
              <Skeleton key={i} variant="rounded" height={36} />
            ))}
          </Stack>
        ) : groups.length === 0 ? (
          <Box sx={{ px: 2, pt: 2 }}>
            <Typography variant="caption" color="text.secondary">
              No conversations yet. Ask your first question!
            </Typography>
          </Box>
        ) : (
          groups.map(group => (
            <Box key={group.label} sx={{ mb: 1 }}>
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{ px: 1.5, fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase' }}
              >
                {group.label}
              </Typography>
              <Stack spacing={0} sx={{ mt: 0.5 }}>
                {group.items.map(s => (
                  <Box
                    key={s.sessionId}
                    onClick={() => navigate(`/chat/${s.sessionId}`)}
                    sx={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 1,
                      px: 1.5,
                      py: 0.75,
                      cursor: 'pointer',
                      borderRadius: 1,
                      mx: 0.5,
                      bgcolor:
                        activeSessionId === s.sessionId
                          ? 'action.selected'
                          : 'transparent',
                      '&:hover': {
                        bgcolor:
                          activeSessionId === s.sessionId
                            ? 'action.selected'
                            : 'action.hover',
                      },
                      transition: 'background-color 0.15s',
                    }}
                  >
                    <ChatBubbleOutlineIcon
                      sx={{ fontSize: 14, color: 'text.secondary', flexShrink: 0 }}
                    />
                    <Typography
                      variant="body2"
                      noWrap
                      sx={{
                        flex: 1,
                        fontWeight: activeSessionId === s.sessionId ? 600 : 400,
                      }}
                    >
                      {s.title ?? 'New conversation'}
                    </Typography>
                  </Box>
                ))}
              </Stack>
            </Box>
          ))
        )}
      </Box>

      <Divider />

      {/* Footer: theme toggle + logout */}
      <Stack
        direction="row"
        justifyContent="space-between"
        alignItems="center"
        sx={{ px: 1.5, py: 1 }}
      >
        <Tooltip title={mode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}>
          <IconButton size="small" onClick={onToggleMode}>
            {mode === 'dark' ? <LightModeIcon fontSize="small" /> : <DarkModeIcon fontSize="small" />}
          </IconButton>
        </Tooltip>
        <Tooltip title="Logout">
          <IconButton size="small" onClick={handleLogout}>
            <LogoutIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      </Stack>
    </Box>
  );
}
