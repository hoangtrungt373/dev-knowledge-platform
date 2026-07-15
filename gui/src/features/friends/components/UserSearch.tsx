import { useCallback, useEffect, useState } from 'react';
import { Box, CircularProgress, InputAdornment, Pagination, Stack, TextField, Typography } from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import { friendApi } from '../api/friendApi';
import { userApi } from '../api/userApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import { RelationshipStatus, UserSearchResult } from '../types';
import UserRow from './UserRow';
import RelationshipActionButton from './RelationshipActionButton';

const PAGE_SIZE = 20;

/** "Find People" tab — search, one row per result, action button driven by RelationshipStatus. */
export default function UserSearch(): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const [searchInput, setSearchInput] = useState('');
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<UserSearchResult[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [busyUuid, setBusyUuid] = useState<string | null>(null);

  // Debounce search input, same pattern as the admin list pages (e.g. TagListPage).
  useEffect(() => {
    const t = setTimeout(() => { setQuery(searchInput); setPage(0); }, 300);
    return () => clearTimeout(t);
  }, [searchInput]);

  const fetchResults = useCallback(async () => {
    if (!query.trim()) { setResults([]); setTotal(0); return; }
    setLoading(true);
    try {
      const data = await userApi.searchUsers({ q: query, page, size: PAGE_SIZE }, showError);
      setResults(data.content);
      setTotal(data.totalElements);
    } finally {
      setLoading(false);
    }
  }, [query, page, showError]);

  useEffect(() => { fetchResults(); }, [fetchResults]);

  const patchStatus = (userUuid: string, status: RelationshipStatus) => {
    setResults(prev => prev.map(r => (r.user.userUuid === userUuid ? { ...r, relationshipStatus: status } : r)));
  };

  const runAction = async (
    result: UserSearchResult,
    action: () => Promise<unknown>,
    successMessage: string,
    nextStatus: RelationshipStatus,
  ) => {
    setBusyUuid(result.user.userUuid);
    try {
      await action();
      showSuccess(successMessage);
      patchStatus(result.user.userUuid, nextStatus);
    } catch {
      // showError already called by httpClient
    } finally {
      setBusyUuid(null);
    }
  };

  return (
    <Box>
      <TextField
        placeholder="Search by username, name, or exact email…"
        value={searchInput}
        onChange={e => setSearchInput(e.target.value)}
        size="small"
        fullWidth
        sx={{ mb: 2 }}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start"><SearchIcon fontSize="small" color="action" /></InputAdornment>
          ),
        }}
      />

      {!query.trim() ? (
        <Box sx={{ py: 6, textAlign: 'center' }}>
          <Typography color="text.secondary">Search for people to add as friends.</Typography>
        </Box>
      ) : loading && results.length === 0 ? (
        <Box sx={{ py: 6, textAlign: 'center' }}><CircularProgress size={28} /></Box>
      ) : results.length === 0 ? (
        <Box sx={{ py: 6, textAlign: 'center' }}>
          <Typography color="text.secondary">No users found for &quot;{query}&quot;.</Typography>
        </Box>
      ) : (
        <Stack divider={<Box sx={{ borderBottom: 1, borderColor: 'divider' }} />}>
          {results.map(result => (
            <UserRow
              key={result.user.userUuid}
              user={result.user}
              subtitle={
                result.mutualFriendCount > 0
                  ? `${result.mutualFriendCount} mutual friend${result.mutualFriendCount === 1 ? '' : 's'}`
                  : undefined
              }
              actions={
                <RelationshipActionButton
                  status={result.relationshipStatus}
                  loading={busyUuid === result.user.userUuid}
                  onSendRequest={() => runAction(
                    result,
                    () => friendApi.sendRequest(result.user.userUuid),
                    `Friend request sent to ${result.user.username}`,
                    'REQUEST_SENT',
                  )}
                  onUnfriend={() => runAction(
                    result,
                    () => friendApi.unfriend(result.user.userUuid),
                    `Unfriended ${result.user.username}`,
                    'STRANGER',
                  )}
                  onBlock={() => runAction(
                    result,
                    () => friendApi.block(result.user.userUuid),
                    `Blocked ${result.user.username}`,
                    'BLOCKED',
                  )}
                  onUnblock={() => runAction(
                    result,
                    () => friendApi.unblock(result.user.userUuid),
                    `Unblocked ${result.user.username}`,
                    'STRANGER',
                  )}
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
