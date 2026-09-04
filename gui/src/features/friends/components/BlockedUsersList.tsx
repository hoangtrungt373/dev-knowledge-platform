import { useCallback, useEffect, useState } from 'react';
import { Box, Button, Pagination, Stack } from '@mui/material';
import BlockIcon from '@mui/icons-material/Block';
import { friendApi } from '../api/friendApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import SectionStatus from '@shared/components/SectionStatus';
import { UserSummary } from '../types';
import UserRow from './UserRow';

const PAGE_SIZE = 20;

export default function BlockedUsersList(): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busyUuid, setBusyUuid] = useState<string | null>(null);

  const fetchBlocked = useCallback(async () => {
    setLoading(true);
    try {
      const data = await friendApi.listBlockedUsers({ page, size: PAGE_SIZE }, showError);
      setUsers(data.content);
      setTotal(data.totalElements);
    } finally {
      setLoading(false);
    }
  }, [page, showError]);

  useEffect(() => { fetchBlocked(); }, [fetchBlocked]);

  const unblock = async (user: UserSummary) => {
    setBusyUuid(user.userUuid);
    try {
      await friendApi.unblock(user.userUuid, showError);
      showSuccess(`Unblocked ${user.username}`);
      fetchBlocked();
    } catch {
      // showError already called by httpClient
    } finally {
      setBusyUuid(null);
    }
  };

  if (users.length === 0) {
    return <SectionStatus loading={loading} isEmpty emptyMessage="You haven't blocked anyone." />;
  }

  return (
    <Stack divider={<Box sx={{ borderBottom: 1, borderColor: 'divider' }} />}>
      {users.map(user => (
        <UserRow
          key={user.userUuid}
          user={user}
          actions={
            <Button
              size="small"
              variant="outlined"
              startIcon={<BlockIcon fontSize="small" />}
              disabled={busyUuid === user.userUuid}
              onClick={() => unblock(user)}
            >
              Unblock
            </Button>
          }
        />
      ))}
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
    </Stack>
  );
}
