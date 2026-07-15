import { ReactNode } from 'react';
import { Box, Stack, Typography } from '@mui/material';
import { UserSummary } from '../types';
import UserAvatar from './UserAvatar';

interface Props {
  user: UserSummary;
  /** e.g. "12 mutual friends", "Friends since Jan 12, 2026" — omit if there's nothing to show. */
  subtitle?: string;
  /** Right-aligned action button(s) — differ per list (Confirm/Delete, Cancel, Friends menu, etc). */
  actions?: ReactNode;
}

/**
 * Generic person row shared by every friends list (search results, requests, friends, blocked) —
 * the one thing all five have in common is "avatar + name + one line of context + an action."
 */
export default function UserRow({ user, subtitle, actions }: Props): JSX.Element {
  const displayName = [user.firstName, user.lastName].filter(Boolean).join(' ') || user.username;

  return (
    <Stack direction="row" alignItems="center" spacing={1.5} sx={{ py: 1.25, px: 1 }}>
      <UserAvatar user={user} />
      <Box sx={{ flexGrow: 1, minWidth: 0 }}>
        <Typography variant="body1" fontWeight={600} noWrap>
          {displayName}
        </Typography>
        <Typography variant="body2" color="text.secondary" noWrap>
          @{user.username}{subtitle ? ` · ${subtitle}` : ''}
        </Typography>
      </Box>
      {actions}
    </Stack>
  );
}
