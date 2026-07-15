import { Box, Stack, Typography, useTheme } from '@mui/material';
import SmartToyOutlinedIcon from '@mui/icons-material/SmartToyOutlined';
import PersonOutlineIcon from '@mui/icons-material/PersonOutline';
import { LocalMessage } from '../types';
import MarkdownRenderer from './MarkdownRenderer';
import SourcesPanel from './SourcesPanel';

interface Props {
  message: LocalMessage;
}

/** Blinking cursor shown while the assistant is streaming. */
function StreamingCursor() {
  return (
    <Box
      component="span"
      sx={{
        display: 'inline-block',
        width: '2px',
        height: '1em',
        bgcolor: 'primary.main',
        ml: '2px',
        verticalAlign: 'text-bottom',
        animation: 'blink 1s step-end infinite',
        '@keyframes blink': {
          '0%,100%': { opacity: 1 },
          '50%': { opacity: 0 },
        },
      }}
    />
  );
}

/** Renders one chat message — user bubble on the right, assistant on the left. */
export default function MessageBubble({ message }: Props) {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';
  const isUser = message.role === 'USER';

  if (isUser) {
    return (
      <Stack direction="row" justifyContent="flex-end" sx={{ mb: 2 }}>
        <Box
          sx={{
            maxWidth: '72%',
            bgcolor: 'primary.main',
            color: isDark ? '#0d1117' : '#ffffff',
            borderRadius: '18px 18px 4px 18px',
            px: 2,
            py: 1.25,
          }}
        >
          <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap', lineHeight: 1.6 }}>
            {message.content}
          </Typography>
        </Box>
      </Stack>
    );
  }

  return (
    <Stack direction="row" spacing={1.5} alignItems="flex-start" sx={{ mb: 2 }}>
      {/* Bot avatar */}
      <Box
        sx={{
          width: 32,
          height: 32,
          borderRadius: '50%',
          bgcolor: isDark ? 'rgba(88,166,255,0.12)' : 'rgba(9,105,218,0.10)',
          border: '1px solid',
          borderColor: 'divider',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
          mt: 0.25,
        }}
      >
        <SmartToyOutlinedIcon sx={{ fontSize: 16, color: 'primary.main' }} />
      </Box>

      {/* Message body */}
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Box
          sx={{
            bgcolor: isDark ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.02)',
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: '4px 18px 18px 18px',
            px: 2,
            py: 1.5,
          }}
        >
          {message.content === '' && message.streaming ? (
            <StreamingCursor />
          ) : (
            <>
              <MarkdownRenderer content={message.content} />
              {message.streaming && <StreamingCursor />}
            </>
          )}
        </Box>

        {!message.streaming && message.sources && message.sources.length > 0 && (
          <Box sx={{ px: 0.5 }}>
            <SourcesPanel sources={message.sources} />
          </Box>
        )}
      </Box>
    </Stack>
  );
}
