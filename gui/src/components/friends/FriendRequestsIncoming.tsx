import { useCallback, useEffect, useState } from 'react';
import { Box, Button, ButtonGroup, CircularProgress, Pagination, Stack, Typography } from '@mui/material';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import { friendApi } from '../../api';
import { useNotification } from '../../contexts/NotificationContext';
import { FriendRequest } from '../../types';
import UserRow from './UserRow';

const PAGE_SIZE = 20;

interface Props {
  /** Lets FriendsPage keep the "Requests" tab badge in sync without a separate fetch. */
  onCountChange?: (count: number) => void;
}

export default function FriendRequestsIncoming({ onCountChange }: Props): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const [requests, setRequests] = useState<FriendRequest[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);

  const fetchRequests = useCallback(async () => {
    setLoading(true);
    try {
      const data = await friendApi.listIncomingRequests({ page, size: PAGE_SIZE }, showError);
      setRequests(data.content);
      setTotal(data.totalElements);
      onCountChange?.(data.totalElements);
    } finally {
      setLoading(false);
    }
  }, [page, showError, onCountChange]);

  useEffect(() => { fetchRequests(); }, [fetchRequests]);

  const respond = async (request: FriendRequest, action: 'accept' | 'reject') => {
    setBusyId(request.id);
    try {
      if (action === 'accept') {
        await friendApi.acceptRequest(request.id, showError);
        showSuccess(`You and ${request.requester.username} are now friends`);
      } else {
        await friendApi.rejectRequest(request.id, showError);
        showSuccess('Request declined');
      }
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
        <Typography color="text.secondary">No pending friend requests.</Typography>
      </Box>
    );
  }

  return (
    <Stack divider={<Box sx={{ borderBottom: 1, borderColor: 'divider' }} />}>
      {requests.map(request => (
        <UserRow
          key={request.id}
          user={request.requester}
          subtitle={`Sent ${new Date(request.createdAt).toLocaleDateString()}`}
          actions={
            <ButtonGroup size="small">
              <Button
                variant="contained"
                startIcon={<CheckIcon fontSize="small" />}
                disabled={busyId === request.id}
                onClick={() => respond(request, 'accept')}
              >
                Confirm
              </Button>
              <Button
                startIcon={<CloseIcon fontSize="small" />}
                disabled={busyId === request.id}
                onClick={() => respond(request, 'reject')}
              >
                Delete
              </Button>
            </ButtonGroup>
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
