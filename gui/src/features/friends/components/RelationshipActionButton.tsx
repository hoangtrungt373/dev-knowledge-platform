import { Button, Chip, IconButton, Stack, Tooltip } from '@mui/material';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty';
import MarkEmailUnreadIcon from '@mui/icons-material/MarkEmailUnread';
import BlockIcon from '@mui/icons-material/Block';
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutline';
import { RelationshipStatus } from '../types';
import FriendsMenuButton from './FriendsMenuButton';

interface Props {
  status: RelationshipStatus;
  loading?: boolean;
  onSendRequest: () => void;
  onUnfriend: () => void;
  onBlock: () => void;
  onUnblock: () => void;
  /** Opens a DM with this user — only reachable from the FRIENDS case. */
  onMessage: () => void;
}

/**
 * Renders the one action appropriate for a search result's RelationshipStatus — mirrors the
 * exhaustive-switch discipline social-service's backend uses over FriendRequestStatus (see
 * social-service/CLAUDE.md): every case is handled explicitly and there's no `default`, so adding
 * a new RelationshipStatus value is a compile error here (not all code paths return a value)
 * until this switch is updated too.
 *
 * REQUEST_SENT/REQUEST_RECEIVED render as informational chips, not actionable buttons —
 * UserSearchResultResponse doesn't carry a friend-request id, so cancel/accept/reject (which the
 * backend keys by request id, not user uuid) can't be called from a search result. Use the
 * Requests tabs (FriendRequestsIncoming/Outgoing, backed by FriendRequest[] which does have an
 * id) for those actions.
 */
export default function RelationshipActionButton({
  status,
  loading,
  onSendRequest,
  onUnfriend,
  onBlock,
  onUnblock,
  onMessage,
}: Props): JSX.Element {
  switch (status) {
    case 'STRANGER':
      return (
        <Button
          size="small"
          variant="contained"
          startIcon={<PersonAddIcon fontSize="small" />}
          onClick={onSendRequest}
          disabled={loading}
        >
          Add Friend
        </Button>
      );
    case 'REQUEST_SENT':
      return <Chip size="small" icon={<HourglassEmptyIcon />} label="Request sent" variant="outlined" />;
    case 'REQUEST_RECEIVED':
      return (
        <Chip
          size="small"
          icon={<MarkEmailUnreadIcon />}
          label="Sent you a request"
          color="primary"
          variant="outlined"
        />
      );
    case 'FRIENDS':
      return (
        <Stack direction="row" spacing={0.5} alignItems="center">
          <Tooltip title="Message">
            <IconButton size="small" onClick={onMessage} disabled={loading}>
              <ChatBubbleOutlineIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          <FriendsMenuButton onUnfriend={onUnfriend} onBlock={onBlock} disabled={loading} />
        </Stack>
      );
    case 'BLOCKED':
      return (
        <Button
          size="small"
          variant="outlined"
          color="inherit"
          startIcon={<BlockIcon fontSize="small" />}
          onClick={onUnblock}
          disabled={loading}
        >
          Unblock
        </Button>
      );
  }
}
