import { useCallback, useEffect, useMemo, useState } from 'react';
import { Box, CircularProgress, InputAdornment, Pagination, Stack, TextField, Typography } from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import { friendApi } from '../../api';
import { useNotification } from '../../contexts/NotificationContext';
import { FriendSummary } from '../../types';
import UserRow from './UserRow';
import FriendsMenuButton from './FriendsMenuButton';

const PAGE_SIZE = 24;

export default function FriendsList(): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const [friends, setFriends] = useState<FriendSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busyUuid, setBusyUuid] = useState<string | null>(null);
  const [filter, setFilter] = useState('');

  const fetchFriends = useCallback(async () => {
    setLoading(true);
    try {
      const data = await friendApi.listFriends(
        { page, size: PAGE_SIZE, sortBy: 'dteCreation', sortDir: 'desc' },
        showError,
      );
      setFriends(data.content);
      setTotal(data.totalElements);
    } finally {
      setLoading(false);
    }
  }, [page, showError]);

  useEffect(() => { fetchFriends(); }, [fetchFriends]);

  // Client-side filter over the currently loaded page only — listFriends has no `q` parameter
  // on the backend, so this is not a full search across every friend, just this page's rows.
  const visible = useMemo(() => {
    const q = filter.trim().toLowerCase();
    if (!q) return friends;
    return friends.filter(f =>
      f.user.username.toLowerCase().includes(q) ||
      `${f.user.firstName ?? ''} ${f.user.lastName ?? ''}`.toLowerCase().includes(q),
    );
  }, [friends, filter]);

  const unfriend = async (friend: FriendSummary) => {
    setBusyUuid(friend.user.userUuid);
    try {
      await friendApi.unfriend(friend.user.userUuid, showError);
      showSuccess(`Unfriended ${friend.user.username}`);
      fetchFriends();
    } catch {
      // showError already called
    } finally {
      setBusyUuid(null);
    }
  };

  const block = async (friend: FriendSummary) => {
    setBusyUuid(friend.user.userUuid);
    try {
      await friendApi.block(friend.user.userUuid, showError);
      showSuccess(`Blocked ${friend.user.username}`);
      fetchFriends();
    } catch {
      // showError already called
    } finally {
      setBusyUuid(null);
    }
  };

  return (
    <Box>
      <TextField
        placeholder="Filter friends on this page…"
        value={filter}
        onChange={e => setFilter(e.target.value)}
        size="small"
        fullWidth
        sx={{ mb: 2 }}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start"><SearchIcon fontSize="small" color="action" /></InputAdornment>
          ),
        }}
      />

      {loading && friends.length === 0 ? (
        <Box sx={{ py: 6, textAlign: 'center' }}><CircularProgress size={28} /></Box>
      ) : visible.length === 0 ? (
        <Box sx={{ py: 6, textAlign: 'center' }}>
          <Typography color="text.secondary">
            {friends.length === 0 ? "You haven't added any friends yet." : 'No friends match your filter.'}
          </Typography>
        </Box>
      ) : (
        <Stack divider={<Box sx={{ borderBottom: 1, borderColor: 'divider' }} />}>
          {visible.map(friend => (
            <UserRow
              key={friend.user.userUuid}
              user={friend.user}
              subtitle={`Friends since ${new Date(friend.friendsSince).toLocaleDateString()}`}
              actions={
                <FriendsMenuButton
                  disabled={busyUuid === friend.user.userUuid}
                  onUnfriend={() => unfriend(friend)}
                  onBlock={() => block(friend)}
                />
              }
            />
          ))}
        </Stack>
      )}

      {total > PAGE_SIZE && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
          <Pagination
            count={Math.ceil(total / PAGE_SIZE)}
            page={page + 1}
            onChange={(_, p) => setPage(p - 1)}
            size="small"
          />
        </Box>
      )}
    </Box>
  );
}
