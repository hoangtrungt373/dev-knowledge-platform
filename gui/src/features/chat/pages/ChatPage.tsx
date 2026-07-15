import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Box, Stack, Typography, useTheme } from '@mui/material';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import { useChatStream } from '../hooks/useChatStream';
import SessionSidebar from '../components/SessionSidebar';
import MessageBubble from '../components/MessageBubble';
import ChatInput from '../components/ChatInput';
import { ThemeMode } from '@app/theme';

interface Props {
  mode: ThemeMode;
  onToggleMode: () => void;
}

const SUGGESTIONS = [
  'What is a B-tree index and when should I use it?',
  'Explain the SOLID principles with examples',
  'How does Spring Boot autoconfiguration work?',
  'What is the difference between REST and GraphQL?',
];

/** Full-viewport chat page (NavBar hidden). Mirrors the ChatGPT / Claude layout. */
export default function ChatPage({ mode, onToggleMode }: Props) {
  const { sessionId: urlSessionId } = useParams<{ sessionId?: string }>();
  const navigate = useNavigate();
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

  const { messages, streaming, sessionId, sendMessage, abort, loadHistory, resetMessages } =
    useChatStream();

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [refreshTick, setRefreshTick] = useState(0);

  // Load history when the URL session changes
  useEffect(() => {
    if (urlSessionId) {
      loadHistory(Number(urlSessionId));
    } else {
      resetMessages();
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [urlSessionId]);

  // Push the new session ID into the URL after the first message
  useEffect(() => {
    if (sessionId && !urlSessionId) {
      navigate(`/chat/${sessionId}`, { replace: true });
    }
  }, [sessionId, urlSessionId, navigate]);

  // Auto-scroll to the latest message
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = async (question: string) => {
    await sendMessage(question);
    // Bump the sidebar refresh so the new/updated session title appears
    setRefreshTick(t => t + 1);
  };

  const handleNewChat = () => {
    resetMessages();
    navigate('/chat');
  };

  const isEmpty = messages.length === 0;

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      {/* ── Left sidebar ───────────────────────────────────────────── */}
      <SessionSidebar
        activeSessionId={sessionId}
        onNewChat={handleNewChat}
        refreshTick={refreshTick}
        mode={mode}
        onToggleMode={onToggleMode}
      />

      {/* ── Main chat area ─────────────────────────────────────────── */}
      <Box
        sx={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
          bgcolor: 'background.default',
        }}
      >
        {/* Messages scroll area */}
        <Box sx={{ flex: 1, overflowY: 'auto', px: { xs: 2, md: 4 }, py: 3 }}>
          {isEmpty ? (
            <WelcomeScreen onSuggest={q => handleSend(q)} isDark={isDark} />
          ) : (
            <>
              {messages.map(m => (
                <MessageBubble key={m.id} message={m} />
              ))}
              <div ref={messagesEndRef} />
            </>
          )}
        </Box>

        {/* Input bar — always anchored at the bottom */}
        <ChatInput
          onSend={handleSend}
          onStop={abort}
          disabled={streaming}
          streaming={streaming}
        />
      </Box>
    </Box>
  );
}

// ── Welcome screen ───────────────────────────────────────────────────────────

function WelcomeScreen({
  onSuggest,
  isDark,
}: {
  onSuggest: (q: string) => void;
  isDark: boolean;
}) {
  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        height: '100%',
        minHeight: 'calc(100vh - 200px)',
        textAlign: 'center',
        userSelect: 'none',
      }}
    >
      {/* Logo mark */}
      <Box
        sx={{
          width: 56,
          height: 56,
          borderRadius: '50%',
          bgcolor: isDark ? 'rgba(88,166,255,0.12)' : 'rgba(9,105,218,0.10)',
          border: '1px solid',
          borderColor: 'divider',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          mb: 2,
        }}
      >
        <AutoAwesomeIcon sx={{ fontSize: 28, color: 'primary.main' }} />
      </Box>

      <Typography variant="h5" fontWeight={700} sx={{ mb: 0.75 }}>
        Dev Knowledge Platform
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 4, maxWidth: 420 }}>
        Ask questions about your articles, Q&A content, and knowledge base.
        I'll search for the most relevant content and answer based on what I find.
      </Typography>

      {/* Suggestion chips */}
      <Stack
        direction="row"
        flexWrap="wrap"
        gap={1}
        justifyContent="center"
        sx={{ maxWidth: 620 }}
      >
        {SUGGESTIONS.map(q => (
          <Box
            key={q}
            onClick={() => onSuggest(q)}
            sx={{
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: 2,
              px: 1.5,
              py: 0.875,
              cursor: 'pointer',
              fontSize: '0.8rem',
              color: 'text.secondary',
              bgcolor: 'background.paper',
              maxWidth: 280,
              textAlign: 'left',
              transition: 'all 0.15s',
              '&:hover': {
                borderColor: 'primary.main',
                color: 'text.primary',
                bgcolor: isDark ? 'rgba(88,166,255,0.06)' : 'rgba(9,105,218,0.04)',
              },
            }}
          >
            {q}
          </Box>
        ))}
      </Stack>
    </Box>
  );
}
