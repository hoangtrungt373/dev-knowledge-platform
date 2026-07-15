import { Avatar } from '@mui/material';
import { UserSummary } from '../types';

interface Props {
  user: UserSummary;
  size?: number;
}

function initials(user: UserSummary): string {
  const first = user.firstName?.[0] ?? user.username[0] ?? '?';
  const last = user.lastName?.[0] ?? '';
  return (first + last).toUpperCase();
}

export default function UserAvatar({ user, size = 40 }: Props): JSX.Element {
  return (
    <Avatar src={user.profilePicture ?? undefined} sx={{ width: size, height: size }}>
      {!user.profilePicture && initials(user)}
    </Avatar>
  );
}
