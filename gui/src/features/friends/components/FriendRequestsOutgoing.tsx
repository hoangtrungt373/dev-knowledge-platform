import { useCallback, useEffect, useState } from 'react';
import { Box, Button, CircularProgress, Pagination, Stack, Typography } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import { friendApi } from '../api/friendApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import { FriendRequest } from '../types';
import UserRow from './UserRow';

const PAGE_SIZE = 20;

export default function FriendRequestsOutgoing(): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const [requests, setRequests] = useState<FriendRequest[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);

  const fetchRequests = useCallback(async () => {
    setLoading(true);
    try {
      const data = await friendApi.listOutgoingRequests({ page, size: PAGE_SIZE }, showError);
      setRequests(data.content);
      setTotal(data.totalElements);
    } finally {
      setLoading(false);
    }
  }, [page, showError]);

  useEffect(() => { fetchRequests(); }, [fetchRequests]);

  const cancel = async (request: FriendRequest) => {
    setBusyId(request.id);
    try {
      await friendApi.cancelRequest(request.id, showError);
      showSuccess('Request cancelled');
      fetchRequests();
    } catch {
      // showError already called by httpClient
    } finally {
      setBusyId(null);
    }
  };

  if (loading && requests.length === 0) {
    return <Box sx={{ py: 6, textAlign: 'center' }}><CircularProgress size={28} /></Box>;
  }

  if (requests.length === 0) {
    return (
      <Box sx={{ py: 6, textAlign: 'center' }}>
        <Typography color="text.secondary">No pending sent requests.</Typography>
      </Box>
    );
  }

  return (
    <Stack divider={<Box sx={{ borderBottom: 1, borderColor: 'divider' }} />}>
      {requests.map(request => (
        <UserRow
          key={request.id}
          user={request.addressee}
          subtitle={`Sent ${new Date(request.createdAt).toLocaleDateString()}`}
          actions={
            <Button
              size="small"
              startIcon={<CloseIcon fontSize="small" />}
              disabled={busyId === request.id}
              onClick={() => cancel(request)}
            >
              Cancel Request
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
