import { useEffect, useRef } from 'react';
import { Box, Typography } from '@mui/material';
import { authService } from '@auth/services/authService';
import { DmMessage } from '../types';

interface Props {
  messages: DmMessage[];
}

/**
 * Renders a DM conversation as left/right bubbles (own messages right-aligned/primary color).
 * Written fresh rather than extending @chat's MessageBubble — that component is AI-chat-specific
 * (streaming cursor, markdown rendering, sources panel), not a good fit to extend for plain DMs.
 */
export default function MessageList({ messages }: Props): JSX.Element {
  const myUuid = authService.getUserUuid();
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length]);

  return (
    <Box sx={{ flex: 1, overflowY: 'auto', px: 2, py: 2 }}>
      {messages.map(message => {
        const isOwn = message.sender.userUuid === myUuid;
        return (
          <Box
            key={message.id}
            sx={{ display: 'flex', justifyContent: isOwn ? 'flex-end' : 'flex-start', mb: 1 }}
          >
            <Box
              sx={{
                maxWidth: '70%',
                bgcolor: isOwn ? 'primary.main' : 'action.selected',
                color: isOwn ? 'primary.contrastText' : 'text.primary',
                borderRadius: 2,
                px: 1.5,
                py: 1,
              }}
            >
              <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                {message.content}
              </Typography>
              <Typography variant="caption" sx={{ display: 'block', mt: 0.25, opacity: 0.7, textAlign: 'right' }}>
                {new Date(message.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
              </Typography>
            </Box>
          </Box>
        );
      })}
      <div ref={endRef} />
    </Box>
  );
}
